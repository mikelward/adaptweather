package app.clothescast.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.clothescast.core.domain.model.DeliveryMode
import app.clothescast.core.domain.model.DistanceUnit
import app.clothescast.core.domain.model.Region
import app.clothescast.core.domain.model.Schedule
import app.clothescast.core.domain.model.TemperatureUnit
import app.clothescast.core.domain.model.VoiceLocale
import app.clothescast.core.domain.model.WardrobeRule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @TempDir lateinit var tempDir: Path

    private val zone: ZoneId = ZoneId.of("Europe/London")
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var subject: SettingsRepository

    @BeforeEach
    fun setUp() {
        // See SecureKeyStoreTest.setUp for why we install a test Main dispatcher even
        // though this class doesn't directly read Dispatchers.Main, and why we pass an
        // explicit scheduler instead of relying on the no-arg dispatcher constructor.
        Dispatchers.setMain(UnconfinedTestDispatcher(TestCoroutineScheduler()))
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir.toFile(), "settings.preferences_pb") },
        )
        subject = SettingsRepository(
            dataStore = dataStore,
            zoneIdProvider = { zone },
            // Pin the locale so default-unit assertions don't drift with the
            // host machine's locale (CI runs on en_US, dev machines vary).
            systemLocaleProvider = { Locale.UK },
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `defaults are returned when nothing is stored`() = runTest {
        val prefs = subject.preferences.first()

        prefs.schedule.time shouldBe LocalTime.of(7, 0)
        prefs.schedule.days shouldBe Schedule.EVERY_DAY
        prefs.schedule.zoneId shouldBe zone
        prefs.deliveryMode shouldBe DeliveryMode.NOTIFICATION_ONLY
        prefs.temperatureUnit shouldBe TemperatureUnit.CELSIUS
        prefs.distanceUnit shouldBe DistanceUnit.KILOMETERS
        prefs.wardrobeRules shouldBe WardrobeRule.DEFAULTS
    }

    @Test
    fun `setSchedule round-trips time and days`() = runTest {
        subject.setSchedule(
            time = LocalTime.of(6, 30),
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )

        val prefs = subject.preferences.first()
        prefs.schedule.time shouldBe LocalTime.of(6, 30)
        prefs.schedule.days.shouldContainExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    }

    @Test
    fun `setSchedule rejects empty days`() = runTest {
        shouldThrow<IllegalArgumentException> {
            subject.setSchedule(LocalTime.of(7, 0), emptySet())
        }
    }

    @Test
    fun `setDeliveryMode round-trips`() = runTest {
        subject.setDeliveryMode(DeliveryMode.NOTIFICATION_AND_TTS)

        subject.preferences.first().deliveryMode shouldBe DeliveryMode.NOTIFICATION_AND_TTS
    }

    @Test
    fun `region defaults to SYSTEM when nothing stored`() = runTest {
        subject.preferences.first().region shouldBe Region.SYSTEM
    }

    @Test
    fun `setRegion round-trips`() = runTest {
        subject.setRegion(Region.EN_US)
        subject.preferences.first().region shouldBe Region.EN_US
    }

    @Test
    fun `temperature default follows region locale - en-US picks Fahrenheit`() = runTest {
        subject.setRegion(Region.EN_US)
        subject.preferences.first().temperatureUnit shouldBe TemperatureUnit.FAHRENHEIT
    }

    @Test
    fun `distance default follows region locale - en-US picks miles`() = runTest {
        subject.setRegion(Region.EN_US)
        subject.preferences.first().distanceUnit shouldBe DistanceUnit.MILES
    }

    @Test
    fun `temperature default falls back to system locale when region is SYSTEM`() = runTest {
        // Test setUp pins systemLocaleProvider to Locale.UK → metric.
        subject.preferences.first().temperatureUnit shouldBe TemperatureUnit.CELSIUS
        subject.preferences.first().distanceUnit shouldBe DistanceUnit.KILOMETERS
    }

    @Test
    fun `explicitly chosen unit overrides the region-derived default`() = runTest {
        subject.setRegion(Region.EN_US)
        subject.setTemperatureUnit(TemperatureUnit.CELSIUS)
        subject.setDistanceUnit(DistanceUnit.KILOMETERS)

        val prefs = subject.preferences.first()
        prefs.temperatureUnit shouldBe TemperatureUnit.CELSIUS
        prefs.distanceUnit shouldBe DistanceUnit.KILOMETERS
    }

    @Test
    fun `setUnits round-trips both`() = runTest {
        subject.setTemperatureUnit(TemperatureUnit.FAHRENHEIT)
        subject.setDistanceUnit(DistanceUnit.MILES)

        val prefs = subject.preferences.first()
        prefs.temperatureUnit shouldBe TemperatureUnit.FAHRENHEIT
        prefs.distanceUnit shouldBe DistanceUnit.MILES
    }

    @Test
    fun `setWardrobeRules round-trips all three condition types`() = runTest {
        val rules = listOf(
            WardrobeRule("hat", WardrobeRule.TemperatureBelow(5.0)),
            WardrobeRule("shorts", WardrobeRule.TemperatureAbove(28.5)),
            WardrobeRule("brolly", WardrobeRule.PrecipitationProbabilityAbove(40.0)),
        )

        subject.setWardrobeRules(rules)

        subject.preferences.first().wardrobeRules shouldContainExactly rules
    }

    @Test
    fun `setWardrobeRules with empty list persists empty list, not defaults`() = runTest {
        subject.setWardrobeRules(emptyList())

        subject.preferences.first().wardrobeRules shouldBe emptyList()
    }

    @Test
    fun `corrupt wardrobe rules JSON falls back to defaults`() = runTest {
        dataStore.edit {
            it[stringPreferencesKey("wardrobe_rules_json")] = "{not valid json["
        }

        subject.preferences.first().wardrobeRules shouldBe WardrobeRule.DEFAULTS
    }

    @Test
    fun `unknown enum names fall back to defaults`() = runTest {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("delivery_mode")] = "UNKNOWN_MODE"
            prefs[stringPreferencesKey("temperature_unit")] = "KELVIN"
            prefs[stringSetPreferencesKey("schedule_days")] = setOf("FUNDAY")
        }

        val prefs = subject.preferences.first()
        prefs.deliveryMode shouldBe DeliveryMode.NOTIFICATION_ONLY
        prefs.temperatureUnit shouldBe TemperatureUnit.CELSIUS
        prefs.schedule.days shouldBe Schedule.EVERY_DAY
    }

    @Test
    fun `voice setters round-trip and are independent across providers`() = runTest {
        subject.setGeminiVoice("Puck")
        subject.setOpenAiVoice("nova")
        subject.setElevenLabsVoice("21m00Tcm4TlvDq8ikWAM")

        val prefs = subject.preferences.first()
        prefs.geminiVoice shouldBe "Puck"
        prefs.openAiVoice shouldBe "nova"
        prefs.elevenLabsVoice shouldBe "21m00Tcm4TlvDq8ikWAM"
    }

    @Test
    fun `gemini voice defaults to Kore when nothing stored`() = runTest {
        subject.preferences.first().geminiVoice shouldBe "Kore"
    }

    @Test
    fun `elevenLabs voice defaults to Sarah when nothing stored`() = runTest {
        subject.preferences.first().elevenLabsVoice shouldBe "EXAVITQu4vr4xnSDxMaL"
    }

    @Test
    fun `openAiVoice defaults to nova for non-British locale preferences`() = runTest {
        subject.setVoiceLocale(VoiceLocale.EN_US)
        subject.preferences.first().openAiVoice shouldBe "nova"

        subject.setVoiceLocale(VoiceLocale.EN_AU)
        subject.preferences.first().openAiVoice shouldBe "nova"
    }

    @Test
    fun `openAiVoice defaults to fable for the en-GB locale preference`() = runTest {
        subject.setVoiceLocale(VoiceLocale.EN_GB)
        subject.preferences.first().openAiVoice shouldBe "fable"
    }

    @Test
    fun `explicitly chosen openAiVoice overrides the locale-derived default`() = runTest {
        subject.setVoiceLocale(VoiceLocale.EN_GB)
        subject.setOpenAiVoice("alloy")

        subject.preferences.first().openAiVoice shouldBe "alloy"
    }

    @Test
    fun `useCalendarEvents defaults to false and round-trips`() = runTest {
        subject.preferences.first().useCalendarEvents shouldBe false

        subject.setUseCalendarEvents(true)
        subject.preferences.first().useCalendarEvents shouldBe true

        subject.setUseCalendarEvents(false)
        subject.preferences.first().useCalendarEvents shouldBe false
    }

    @Test
    fun `zoneId is resolved fresh on each emission`() = runTest {
        val zones = mutableListOf(ZoneId.of("UTC"), ZoneId.of("America/New_York"))
        val rotating = SettingsRepository(
            dataStore = dataStore,
            zoneIdProvider = { zones.removeAt(0) },
        )

        rotating.preferences.first().schedule.zoneId shouldBe ZoneId.of("UTC")
        rotating.preferences.first().schedule.zoneId shouldBe ZoneId.of("America/New_York")
    }
}
