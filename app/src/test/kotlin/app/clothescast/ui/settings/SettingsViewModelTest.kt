package app.clothescast.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.clothescast.core.data.location.OpenMeteoGeocodingClient
import app.clothescast.core.domain.model.CastLength
import app.clothescast.core.domain.model.DeliveryMode
import app.clothescast.core.domain.model.DistanceUnit
import app.clothescast.core.domain.model.TemperatureUnit
import app.clothescast.data.SecureKeyStore
import app.clothescast.data.SettingsRepository
import com.google.crypto.tink.Aead
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json as KotlinxJson
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @TempDir lateinit var tempDir: Path

    // Explicit scheduler avoids the dispatcher's internal Dispatchers.Main lookup, which
    // crashes on Android JVM unit tests because AndroidDispatcherFactory calls the
    // unmocked Looper.getMainLooper(). See SecureKeyStoreTest for the same workaround.
    private val dispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
    // DataStore's default scope is `Dispatchers.IO + SupervisorJob()` — its internal
    // actor coroutine outlives the test body and, when it resumes after `resetMain`,
    // can't dispatch back through TestMainDispatcher (which delegates to the now-
    // missing Dispatchers.Main) and crashes the JVM with "Module with the Main
    // dispatcher is missing". Pinning DataStore to our test dispatcher and cancelling
    // the scope in tearDown ensures DataStore's lifecycle is bounded by the test.
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var settingsDataStore: DataStore<Preferences>
    private lateinit var keyDataStore: DataStore<Preferences>
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var keyStore: SecureKeyStore
    private lateinit var subject: SettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        dataStoreScope = CoroutineScope(dispatcher + SupervisorJob())
        settingsDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempDir.toFile(), "settings.preferences_pb") },
        )
        keyDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempDir.toFile(), "key.preferences_pb") },
        )
        settingsRepository = SettingsRepository(settingsDataStore)
        keyStore = SecureKeyStore(IdentityFakeAead, keyDataStore)
        // Geocoding client wired with a Mock engine that always returns an empty response;
        // the tests in this class don't exercise location lookup.
        val emptyGeocoding = HttpClient(
            MockEngine {
                respond(
                    content = ByteReadChannel("""{"results":[]}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) { install(ContentNegotiation) { json(KotlinxJson { ignoreUnknownKeys = true }) } }
        subject = SettingsViewModel(
            settingsRepository,
            keyStore,
            rearmAlarm = { _, _ -> },
            cancelAlarm = { _ -> },
            geocodingClient = OpenMeteoGeocodingClient(emptyGeocoding),
        )
    }

    @AfterEach
    fun tearDown() {
        dataStoreScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects repository defaults`() = runTest {
        // The initial MutableStateFlow value matches our defaults; await the first
        // collector emission so anything from DataStore that disagreed would surface here.
        val state = subject.state.first {
            !it.apiKeyConfigured && it.deliveryMode == DeliveryMode.NOTIFICATION_ONLY
        }
        state.temperatureUnit shouldBe TemperatureUnit.CELSIUS
        state.distanceUnit shouldBe DistanceUnit.KILOMETERS
    }

    @Test
    fun `setApiKey persists to keystore and updates state`() = runTest {
        subject.setApiKey("AIzaSyTestKeyValue")
        subject.state.first { it.apiKeyConfigured }
        keyStore.get() shouldBe "AIzaSyTestKeyValue"
    }

    @Test
    fun `setApiKey trims whitespace`() = runTest {
        subject.setApiKey("   AIzaSyKey   ")
        subject.state.first { it.apiKeyConfigured }
        keyStore.get() shouldBe "AIzaSyKey"
    }

    @Test
    fun `clearApiKey removes the stored key and updates state`() = runTest {
        subject.setApiKey("AIzaSyKey")
        subject.state.first { it.apiKeyConfigured }
        subject.clearApiKey()
        subject.state.first { !it.apiKeyConfigured }
    }

    @Test
    fun `setDeliveryMode persists`() = runTest {
        subject.setDeliveryMode(DeliveryMode.NOTIFICATION_AND_TTS)
        subject.state.first { it.deliveryMode == DeliveryMode.NOTIFICATION_AND_TTS }
        settingsRepository.preferences.first().deliveryMode shouldBe DeliveryMode.NOTIFICATION_AND_TTS
    }

    @Test
    fun `setTonightDeliveryMode persists independently of deliveryMode`() = runTest {
        subject.setDeliveryMode(DeliveryMode.NOTIFICATION_ONLY)
        subject.setTonightDeliveryMode(DeliveryMode.TTS_ONLY)
        subject.state.first {
            it.deliveryMode == DeliveryMode.NOTIFICATION_ONLY &&
                it.tonightDeliveryMode == DeliveryMode.TTS_ONLY
        }
        val prefs = settingsRepository.preferences.first()
        prefs.deliveryMode shouldBe DeliveryMode.NOTIFICATION_ONLY
        prefs.tonightDeliveryMode shouldBe DeliveryMode.TTS_ONLY
    }

    @Test
    fun `setUseCalendarEvents persists and surfaces in state`() = runTest {
        subject.setUseCalendarEvents(true)
        subject.state.first { it.useCalendarEvents }
        settingsRepository.preferences.first().useCalendarEvents shouldBe true

        subject.setUseCalendarEvents(false)
        subject.state.first { !it.useCalendarEvents }
        settingsRepository.preferences.first().useCalendarEvents shouldBe false
    }

    @Test
    fun `setCastLength persists and surfaces in state`() = runTest {
        subject.setCastLength(CastLength.LONGER)
        subject.state.first { it.castLength == CastLength.LONGER }
        settingsRepository.preferences.first().castLength shouldBe CastLength.LONGER

        subject.setCastLength(CastLength.SHORTER)
        subject.state.first { it.castLength == CastLength.SHORTER }
        settingsRepository.preferences.first().castLength shouldBe CastLength.SHORTER
    }

    @Test
    fun `setTemperatureUnit and setDistanceUnit persist independently`() = runTest {
        subject.setTemperatureUnit(TemperatureUnit.FAHRENHEIT)
        subject.setDistanceUnit(DistanceUnit.MILES)

        val state = subject.state.first {
            it.temperatureUnit == TemperatureUnit.FAHRENHEIT && it.distanceUnit == DistanceUnit.MILES
        }
        state.temperatureUnit shouldBe TemperatureUnit.FAHRENHEIT
        state.distanceUnit shouldBe DistanceUnit.MILES
    }
}

/** Pass-through AEAD: encrypt/decrypt are identity. Sufficient for round-trip tests. */
private object IdentityFakeAead : Aead {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray = plaintext
    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray = ciphertext
}
