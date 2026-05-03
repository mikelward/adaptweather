package app.clothescast.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.clothescast.core.data.insight.KeyProvider
import app.clothescast.core.data.location.OpenMeteoGeocodingClient
import app.clothescast.core.data.tts.ElevenLabsTtsClient
import app.clothescast.core.domain.model.DeliveryMode
import app.clothescast.core.domain.model.DistanceUnit
import app.clothescast.core.domain.model.Region
import app.clothescast.core.domain.model.TemperatureUnit
import app.clothescast.data.SecureKeyStore
import app.clothescast.data.SettingsRepository
import app.clothescast.tts.DeviceVoice
import app.clothescast.tts.TtsVoiceEnumerator
import java.util.Locale
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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
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
            voiceEnumerator = EmptyVoiceEnumerator,
        )
    }

    /**
     * The ViewModel calls this from its preferences flow on every locale
     * change; the existing tests don't care about device-voice enumeration
     * so we hand back empty results without any Android plumbing.
     */
    private object EmptyVoiceEnumerator : TtsVoiceEnumerator {
        override suspend fun listVoices(locale: Locale): List<DeviceVoice> = emptyList()
        override suspend fun resolveAutoPick(locale: Locale): DeviceVoice? = null
        override suspend fun findVoice(id: String): DeviceVoice? = null
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
            !it.apiKeyConfigured && it.deliveryMode == DeliveryMode.NOTIFICATION_AND_TTS
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
    fun `selectLocation also disables device location so the manual pick sticks`() = runTest {
        // Without this, the next worker run's device-resolve would write
        // through and immediately overwrite the city the user just picked —
        // defeating the override. The Location page surfaces a disclosure
        // ("Picking a city turns off auto-detect") so the toggle doesn't
        // appear to flip on its own.
        subject.setUseDeviceLocation(true)
        subject.state.first { it.useDeviceLocation }

        subject.selectLocation(
            app.clothescast.core.domain.model.Location(
                latitude = 51.5074,
                longitude = -0.1278,
                displayName = "London",
            ),
        )

        val state = subject.state.first { !it.useDeviceLocation && it.location != null }
        state.location?.displayName shouldBe "London"
        val prefs = settingsRepository.preferences.first()
        prefs.useDeviceLocation shouldBe false
        prefs.location?.displayName shouldBe "London"
    }

    @Test
    fun `setUseDeviceLocation triggers the eager cache refresh only when enabled`() = runTest {
        // The Activity wires this lambda to `FetchAndNotifyWorker.enqueueOneShot`
        // so the user sees their detected city populate within seconds of
        // toggling device location ON, instead of waiting for the next
        // morning worker run. Toggle-off must NOT enqueue — there's nothing
        // for the worker to refresh once device-location is off.
        var refreshCount = 0
        val refreshSubject = SettingsViewModel(
            settingsRepository = settingsRepository,
            keyStore = keyStore,
            rearmAlarm = { _, _ -> },
            cancelAlarm = { _ -> },
            geocodingClient = OpenMeteoGeocodingClient(
                HttpClient(MockEngine { respond("""{"results":[]}""") }) {
                    install(ContentNegotiation) { json(KotlinxJson { ignoreUnknownKeys = true }) }
                },
            ),
            voiceEnumerator = EmptyVoiceEnumerator,
            refreshLocationCache = { refreshCount++ },
        )

        refreshSubject.setUseDeviceLocation(true)
        refreshSubject.state.first { it.useDeviceLocation }
        refreshCount shouldBe 1

        refreshSubject.setUseDeviceLocation(false)
        refreshSubject.state.first { !it.useDeviceLocation }
        refreshCount shouldBe 1
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

    @Test
    fun `refreshElevenLabsVoices populates state from the API response`() = runTest {
        val voicesJson = """
            {"voices":[
              {"voice_id":"v1","name":"Sarah","labels":{"description":"warm"}},
              {"voice_id":"v2","name":"My Clone"}
            ]}
        """.trimIndent()
        val client = ElevenLabsTtsClient(
            httpClient = HttpClient(
                MockEngine {
                    respond(
                        content = ByteReadChannel(voicesJson),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) { install(ContentNegotiation) { json(KotlinxJson { ignoreUnknownKeys = true }) } },
            keyProvider = object : KeyProvider {
                override suspend fun get(): String = "test-key"
            },
        )
        val refreshSubject = SettingsViewModel(
            settingsRepository = settingsRepository,
            keyStore = keyStore,
            rearmAlarm = { _, _ -> },
            cancelAlarm = { _ -> },
            geocodingClient = OpenMeteoGeocodingClient(
                HttpClient(MockEngine { respond("""{"results":[]}""") }) {
                    install(ContentNegotiation) { json(KotlinxJson { ignoreUnknownKeys = true }) }
                },
            ),
            voiceEnumerator = EmptyVoiceEnumerator,
            elevenLabsTtsClient = client,
        )
        // refreshElevenLabsVoices early-returns when the key isn't
        // configured (the UI also gates the button), so persist a key
        // first and wait for the state flag to flip before triggering.
        refreshSubject.setElevenLabsKey("test-key")
        refreshSubject.state.first { it.elevenLabsKeyConfigured }

        refreshSubject.refreshElevenLabsVoices()

        val state = refreshSubject.state.first { it.elevenLabsRefreshedVoices != null }
        state.elevenLabsRefreshing shouldBe false
        val voices = checkNotNull(state.elevenLabsRefreshedVoices)
        voices shouldHaveSize 2
        voices[0].id shouldBe "v1"
        voices[0].displayName shouldBe "Sarah — warm"
        voices[1].id shouldBe "v2"
        voices[1].displayName shouldBe "My Clone"
    }

    @Test
    fun `refreshElevenLabsVoices reports failures via showError and leaves state untouched`() = runTest {
        val client = ElevenLabsTtsClient(
            httpClient = HttpClient(
                MockEngine {
                    respond(
                        content = ByteReadChannel(
                            """{"detail":{"status":"unauthorized","message":"Invalid API key"}}""",
                        ),
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) { install(ContentNegotiation) { json(KotlinxJson { ignoreUnknownKeys = true }) } },
            keyProvider = object : KeyProvider {
                override suspend fun get(): String = "bad-key"
            },
        )
        var reported: String? = null
        val refreshSubject = SettingsViewModel(
            settingsRepository = settingsRepository,
            keyStore = keyStore,
            rearmAlarm = { _, _ -> },
            cancelAlarm = { _ -> },
            geocodingClient = OpenMeteoGeocodingClient(
                HttpClient(MockEngine { respond("""{"results":[]}""") }) {
                    install(ContentNegotiation) { json(KotlinxJson { ignoreUnknownKeys = true }) }
                },
            ),
            voiceEnumerator = EmptyVoiceEnumerator,
            elevenLabsTtsClient = client,
            showError = { reported = it },
        )
        // The early-return covers "no key" so the 401 path needs a
        // configured (but server-rejected) key. Setting a stored key
        // flips the state flag; the request itself will still 401
        // because the MockEngine returns Unauthorized regardless of
        // what the key actually is.
        refreshSubject.setElevenLabsKey("bad-key")
        refreshSubject.state.first { it.elevenLabsKeyConfigured }

        refreshSubject.refreshElevenLabsVoices()

        refreshSubject.state.first { !it.elevenLabsRefreshing }
        refreshSubject.state.value.elevenLabsRefreshedVoices shouldBe null
        checkNotNull(reported).shouldContain("Invalid API key")
    }

    @Test
    fun `refreshElevenLabsVoices no-ops when no ElevenLabs key is configured`() = runTest {
        // The UI hides the Refresh button without a key, but a defensive
        // early-return means even if it's invoked manually (e.g. test
        // wiring) we don't fire a guaranteed-401 request.
        var listVoicesCallCount = 0
        val client = ElevenLabsTtsClient(
            httpClient = HttpClient(
                MockEngine {
                    listVoicesCallCount++
                    respond("""{"voices":[]}""")
                },
            ) { install(ContentNegotiation) { json(KotlinxJson { ignoreUnknownKeys = true }) } },
            keyProvider = object : KeyProvider {
                override suspend fun get(): String = ""
            },
        )
        val refreshSubject = SettingsViewModel(
            settingsRepository = settingsRepository,
            keyStore = keyStore,
            rearmAlarm = { _, _ -> },
            cancelAlarm = { _ -> },
            geocodingClient = OpenMeteoGeocodingClient(
                HttpClient(MockEngine { respond("""{"results":[]}""") }) {
                    install(ContentNegotiation) { json(KotlinxJson { ignoreUnknownKeys = true }) }
                },
            ),
            voiceEnumerator = EmptyVoiceEnumerator,
            elevenLabsTtsClient = client,
        )
        // Wait for the initial keystore probe to land — without it the
        // state flag is still its default `false`, which would also
        // short-circuit but for the wrong reason.
        refreshSubject.state.first { !it.elevenLabsKeyConfigured }

        refreshSubject.refreshElevenLabsVoices()

        // No HTTP traffic, no state change.
        listVoicesCallCount shouldBe 0
        refreshSubject.state.value.elevenLabsRefreshedVoices shouldBe null
        refreshSubject.state.value.elevenLabsRefreshing shouldBe false
    }

    @Test
    fun `re-enumerates device voices when region changes while voiceLocale is SYSTEM`() = runTest {
        // Pin the JVM default so the SYSTEM-fallback locale on the initial
        // enumeration is guaranteed to differ from en-AU below — without this,
        // a CI runner that happened to report en-AU as its default would short
        // the second emission's "effective locale changed" check.
        val originalDefault = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            // A Channel rather than a plain counter: receive() suspends until
            // an enumeration actually fires, which dodges the race where
            // state.first matches the MutableStateFlow's seed value (region is
            // SYSTEM by default in SettingsState) before the preferences flow
            // has emitted and triggered the first enumeration.
            val enumerated = Channel<Locale>(capacity = Channel.UNLIMITED)
            val countingEnumerator = object : TtsVoiceEnumerator {
                override suspend fun listVoices(locale: Locale): List<DeviceVoice> {
                    enumerated.send(locale)
                    return emptyList()
                }
                override suspend fun resolveAutoPick(locale: Locale): DeviceVoice? = null
                override suspend fun findVoice(id: String): DeviceVoice? = null
            }
            val countingSubject = SettingsViewModel(
                settingsRepository = settingsRepository,
                keyStore = keyStore,
                rearmAlarm = { _, _ -> },
                cancelAlarm = { _ -> },
                geocodingClient = OpenMeteoGeocodingClient(
                    HttpClient(MockEngine { respond("""{"results":[]}""") }) {
                        install(ContentNegotiation) { json(KotlinxJson { ignoreUnknownKeys = true }) }
                    },
                ),
                voiceEnumerator = countingEnumerator,
            )
            // Drain the initial enumeration triggered by the first prefs
            // emission (effective locale is the JVM default while region is
            // SYSTEM).
            enumerated.receive() shouldBe Locale.US

            // Changing region while voiceLocale stays SYSTEM changes the
            // effective locale — the device-voice list should be refreshed
            // for en-AU.
            countingSubject.setRegion(Region.EN_AU)

            enumerated.receive() shouldBe Locale.forLanguageTag("en-AU")
        } finally {
            Locale.setDefault(originalDefault)
        }
    }
}

/** Pass-through AEAD: encrypt/decrypt are identity. Sufficient for round-trip tests. */
private object IdentityFakeAead : Aead {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray = plaintext
    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray = ciphertext
}
