package com.adaptweather.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.adaptweather.core.domain.model.DeliveryMode
import com.adaptweather.core.domain.model.DistanceUnit
import com.adaptweather.core.domain.model.TemperatureUnit
import com.adaptweather.data.SecureKeyStore
import com.adaptweather.data.SettingsRepository
import com.google.crypto.tink.Aead
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @TempDir lateinit var tempDir: Path

    // Explicit scheduler avoids the dispatcher's internal Dispatchers.Main lookup, which
    // crashes on Android JVM unit tests because AndroidDispatcherFactory calls the
    // unmocked Looper.getMainLooper(). See SecureKeyStoreTest for the same workaround.
    private val dispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
    private lateinit var settingsDataStore: DataStore<Preferences>
    private lateinit var keyDataStore: DataStore<Preferences>
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var keyStore: SecureKeyStore
    private lateinit var subject: SettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settingsDataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir.toFile(), "settings.preferences_pb") },
        )
        keyDataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir.toFile(), "key.preferences_pb") },
        )
        settingsRepository = SettingsRepository(settingsDataStore)
        keyStore = SecureKeyStore(IdentityFakeAead, keyDataStore)
        subject = SettingsViewModel(settingsRepository, keyStore, rearmAlarm = {})
    }

    @AfterEach
    fun tearDown() {
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
