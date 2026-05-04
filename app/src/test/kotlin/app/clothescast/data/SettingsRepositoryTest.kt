package app.clothescast.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.clothescast.core.domain.model.ClothesRule
import app.clothescast.core.domain.model.DeliveryMode
import app.clothescast.core.domain.model.DistanceUnit
import app.clothescast.core.domain.model.Fact
import app.clothescast.core.domain.model.OutfitSuggestion
import app.clothescast.core.domain.model.Region
import app.clothescast.core.domain.model.Schedule
import app.clothescast.core.domain.model.TemperatureUnit
import app.clothescast.core.domain.model.TtsEngine
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        prefs.deliveryMode shouldBe DeliveryMode.NOTIFICATION_AND_TTS
        prefs.temperatureUnit shouldBe TemperatureUnit.CELSIUS
        prefs.distanceUnit shouldBe DistanceUnit.KILOMETERS
        prefs.clothesRules shouldBe ClothesRule.DEFAULTS
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
    fun `tonightDeliveryMode falls back to deliveryMode when not set`() = runTest {
        // Existing installs that haven't seen the per-period split yet keep their
        // old shared-mode behaviour: tonight inherits the day card's selection.
        subject.setDeliveryMode(DeliveryMode.NOTIFICATION_AND_TTS)

        subject.preferences.first().tonightDeliveryMode shouldBe DeliveryMode.NOTIFICATION_AND_TTS
    }

    @Test
    fun `setTonightDeliveryMode round-trips and is independent of deliveryMode`() = runTest {
        subject.setDeliveryMode(DeliveryMode.NOTIFICATION_ONLY)
        subject.setTonightDeliveryMode(DeliveryMode.TTS_ONLY)

        val prefs = subject.preferences.first()
        prefs.deliveryMode shouldBe DeliveryMode.NOTIFICATION_ONLY
        prefs.tonightDeliveryMode shouldBe DeliveryMode.TTS_ONLY
    }

    @Test
    fun `dailyMentionEveningEvents defaults to true and round-trips`() = runTest {
        subject.preferences.first().dailyMentionEveningEvents shouldBe true

        subject.setDailyMentionEveningEvents(false)
        subject.preferences.first().dailyMentionEveningEvents shouldBe false

        subject.setDailyMentionEveningEvents(true)
        subject.preferences.first().dailyMentionEveningEvents shouldBe true
    }

    @Test
    fun `tonightNotifyOnlyOnEvents defaults to false and round-trips`() = runTest {
        subject.preferences.first().tonightNotifyOnlyOnEvents shouldBe false

        subject.setTonightNotifyOnlyOnEvents(true)
        subject.preferences.first().tonightNotifyOnlyOnEvents shouldBe true

        subject.setTonightNotifyOnlyOnEvents(false)
        subject.preferences.first().tonightNotifyOnlyOnEvents shouldBe false
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
    fun `setClothesRules round-trips all three condition types`() = runTest {
        val rules = listOf(
            ClothesRule("hat", ClothesRule.TemperatureBelow(5.0)),
            ClothesRule("shorts", ClothesRule.TemperatureAbove(28.5)),
            ClothesRule("brolly", ClothesRule.PrecipitationProbabilityAbove(40.0)),
        )

        subject.setClothesRules(rules)

        subject.preferences.first().clothesRules shouldContainExactly rules
    }

    @Test
    fun `setClothesRules round-trips a fahrenheit-typed threshold`() = runTest {
        // The rule remembers what the user typed (65°F), not a converted Celsius
        // approximation — switching unit at display time is reversible.
        val rules = listOf(
            ClothesRule("sweater", ClothesRule.TemperatureBelow(65.0, TemperatureUnit.FAHRENHEIT)),
            ClothesRule("shorts", ClothesRule.TemperatureAbove(80.0, TemperatureUnit.FAHRENHEIT)),
        )

        subject.setClothesRules(rules)

        subject.preferences.first().clothesRules shouldContainExactly rules
    }

    @Test
    fun `legacy clothes-rules JSON without a unit field deserialises as celsius`() = runTest {
        // Pre-unit-aware app versions wrote `{ "item": ..., "type": ..., "value": ... }`
        // with no `unit`. The DTO must still parse them, and the resulting rule
        // must behave exactly as it did before the unit field existed.
        dataStore.edit {
            it[stringPreferencesKey("clothes_rules_json")] = """
                [{"item":"sweater","type":"temp_below","value":18.0},
                 {"item":"shorts","type":"temp_above","value":24.0}]
            """.trimIndent()
        }

        val rules = subject.preferences.first().clothesRules
        rules shouldContainExactly listOf(
            ClothesRule("sweater", ClothesRule.TemperatureBelow(18.0, TemperatureUnit.CELSIUS)),
            ClothesRule("shorts", ClothesRule.TemperatureAbove(24.0, TemperatureUnit.CELSIUS)),
        )
    }

    @Test
    fun `setClothesRules with empty list reads back as defaults`() = runTest {
        // An empty stored list — whether set deliberately or left over from the
        // editable-UI era when a user deleted all their rules — is treated as
        // "no rules configured" and resolves to DEFAULTS at read time. Editing
        // is locked (ClothesSettings is read-only), so otherwise such a user
        // would have no way to recover the defaults.
        subject.setClothesRules(emptyList())

        subject.preferences.first().clothesRules shouldBe ClothesRule.DEFAULTS
    }

    @Test
    fun `corrupt clothes rules JSON falls back to defaults`() = runTest {
        dataStore.edit {
            it[stringPreferencesKey("clothes_rules_json")] = "{not valid json["
        }

        subject.preferences.first().clothesRules shouldBe ClothesRule.DEFAULTS
    }

    @Test
    fun `unknown enum names fall back to defaults`() = runTest {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("delivery_mode")] = "UNKNOWN_MODE"
            prefs[stringPreferencesKey("temperature_unit")] = "KELVIN"
            prefs[stringSetPreferencesKey("schedule_days")] = setOf("FUNDAY")
        }

        val prefs = subject.preferences.first()
        prefs.deliveryMode shouldBe DeliveryMode.NOTIFICATION_AND_TTS
        prefs.temperatureUnit shouldBe TemperatureUnit.CELSIUS
        prefs.schedule.days shouldBe Schedule.EVERY_DAY
    }

    @Test
    fun `gemini voice setter round-trips`() = runTest {
        subject.setGeminiVoice("Puck")
        subject.preferences.first().geminiVoice shouldBe "Puck"
    }

    @Test
    fun `gemini voice defaults to Erinome when nothing stored`() = runTest {
        subject.preferences.first().geminiVoice shouldBe "Erinome"
    }

    @Test
    fun `device voice defaults to null and round-trips`() = runTest {
        subject.preferences.first().deviceVoice shouldBe null

        subject.setDeviceVoice("en-us-x-tpc-network")
        subject.preferences.first().deviceVoice shouldBe "en-us-x-tpc-network"

        // Null clears the pin; preferences flow stops emitting the stored value
        // and the speaker reverts to auto-pick.
        subject.setDeviceVoice(null)
        subject.preferences.first().deviceVoice shouldBe null
    }

    @Test
    fun `device voice setter treats blank as clear`() = runTest {
        subject.setDeviceVoice("en-us-x-tpc-network")
        subject.setDeviceVoice("")
        subject.preferences.first().deviceVoice shouldBe null
    }

    @Test
    fun `setTtsEngineIfUnset writes when nothing is stored`() = runTest {
        subject.preferences.first().ttsEngine shouldBe TtsEngine.DEVICE

        subject.setTtsEngineIfUnset(TtsEngine.GEMINI)
        subject.preferences.first().ttsEngine shouldBe TtsEngine.GEMINI
    }

    @Test
    fun `setTtsEngineIfUnset does not clobber an explicit choice`() = runTest {
        subject.setTtsEngine(TtsEngine.GEMINI)

        subject.setTtsEngineIfUnset(TtsEngine.DEVICE)
        subject.preferences.first().ttsEngine shouldBe TtsEngine.GEMINI
    }

    @Test
    fun `setTtsEngineIfUnset does not clobber an explicit DEVICE choice`() = runTest {
        // The default is also DEVICE, so explicitly picking DEVICE has to
        // count as "set" — otherwise re-running onboarding would silently
        // promote a deliberately-DEVICE user to Gemini.
        subject.setTtsEngine(TtsEngine.DEVICE)

        subject.setTtsEngineIfUnset(TtsEngine.GEMINI)
        subject.preferences.first().ttsEngine shouldBe TtsEngine.DEVICE
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
    fun `outfitThresholds defaults to DEFAULT and round-trips`() = runTest {
        subject.preferences.first().outfitThresholds shouldBe OutfitSuggestion.Thresholds.DEFAULT

        val tuned = OutfitSuggestion.Thresholds.DEFAULT.copy(
            sweaterMaxFeelsLikeMinC = 7.0,
            tshirtMinFeelsLikeMinC = 17.0,
        )
        subject.setOutfitThresholds(tuned)
        subject.preferences.first().outfitThresholds shouldBe tuned
    }

    @Test
    fun `outfitThresholds setter clamps to MIN_C and MAX_C`() = runTest {
        // Out-of-range values can't reach DataStore — even a runaway tap loop
        // can't push past the documented bounds.
        val absurd = OutfitSuggestion.Thresholds(
            sweaterMaxFeelsLikeMinC = -200.0,
            tshirtMinFeelsLikeMinC = 999.0,
            shortsMinFeelsLikeMaxC = 999.0,
            shortsMinFeelsLikeMinC = -200.0,
        )
        subject.setOutfitThresholds(absurd)

        val read = subject.preferences.first().outfitThresholds
        read.sweaterMaxFeelsLikeMinC shouldBe OutfitSuggestion.Thresholds.MIN_C
        read.tshirtMinFeelsLikeMinC shouldBe OutfitSuggestion.Thresholds.MAX_C
        read.shortsMinFeelsLikeMaxC shouldBe OutfitSuggestion.Thresholds.MAX_C
        read.shortsMinFeelsLikeMinC shouldBe OutfitSuggestion.Thresholds.MIN_C
    }

    @Test
    fun `resetOutfitThresholds restores DEFAULT`() = runTest {
        subject.setOutfitThresholds(
            OutfitSuggestion.Thresholds.DEFAULT.with(
                Fact.ThresholdKind.SWEATER_MAX_FEELS_LIKE_MIN,
                4.0,
            ),
        )
        subject.preferences.first().outfitThresholds.sweaterMaxFeelsLikeMinC shouldBe 4.0

        subject.resetOutfitThresholds()
        subject.preferences.first().outfitThresholds shouldBe OutfitSuggestion.Thresholds.DEFAULT
    }

    @Test
    fun `adjustOutfitThreshold serialises rapid taps so none are dropped`() = runTest {
        // Five concurrent `−1°` taps must each see the prior write — final
        // value is the starting threshold minus 5°C, not a single 1°C step
        // from the same pre-update snapshot. DataStore.edit serialises edits,
        // so this exercises the atomic read-modify-write path that protects
        // tap-spam from collapsing into one.
        val starting = OutfitSuggestion.Thresholds.DEFAULT.tshirtMinFeelsLikeMinC
        coroutineScope {
            repeat(5) {
                launch {
                    subject.adjustOutfitThreshold(
                        Fact.ThresholdKind.TSHIRT_MIN_FEELS_LIKE_MIN,
                        -1.0,
                    )
                }
            }
        }
        subject.preferences.first().outfitThresholds.tshirtMinFeelsLikeMinC shouldBe (starting - 5.0)
    }

    @Test
    fun `adjustOutfitThreshold clamps to MIN_C and MAX_C`() = runTest {
        // Even a relentless tap-spam can't escape the documented bounds.
        repeat(100) {
            subject.adjustOutfitThreshold(Fact.ThresholdKind.SWEATER_MAX_FEELS_LIKE_MIN, -1.0)
        }
        subject.preferences.first().outfitThresholds.sweaterMaxFeelsLikeMinC shouldBe
            OutfitSuggestion.Thresholds.MIN_C
    }

    @Test
    fun `dismissedUpdateVersion defaults to 0 and round-trips`() = runTest {
        // Default 0 means "never dismissed" — any non-zero availableVersionCode
        // from Play surfaces the update banner. Storing the dismissed version
        // (rather than a boolean) means a still-newer release re-surfaces the
        // banner automatically.
        subject.dismissedUpdateVersion.first() shouldBe 0

        subject.setDismissedUpdateVersion(72)
        subject.dismissedUpdateVersion.first() shouldBe 72

        subject.setDismissedUpdateVersion(99)
        subject.dismissedUpdateVersion.first() shouldBe 99
    }

    @Test
    fun `dismissedLocalBuildSha defaults to empty and round-trips`() = runTest {
        subject.dismissedLocalBuildSha.first() shouldBe ""

        subject.setDismissedLocalBuildSha("abc1234")
        subject.dismissedLocalBuildSha.first() shouldBe "abc1234"

        subject.setDismissedLocalBuildSha("def5678")
        subject.dismissedLocalBuildSha.first() shouldBe "def5678"
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
