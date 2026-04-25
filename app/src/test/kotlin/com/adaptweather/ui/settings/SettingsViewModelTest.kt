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

    private val dispatcher = UnconfinedTestDispatcher()
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
        subject = SettingsViewModel(settingsRepository, keyStore)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects repository defaults`() = runTest {
        val state = subject.state.value
        state.deliveryMode shouldBe DeliveryMode.NOTIFICATION_ONLY
        state.temperatureUnit shouldBe TemperatureUnit.CELSIUS
        state.distanceUnit shouldBe DistanceUnit.KILOMETERS
        state.apiKeyConfigured shouldBe false
    }

    @Test
    fun `setApiKey persists to keystore and updates state`() = runTest {
        subject.setApiKey("AIzaSyTestKeyValue")
        subject.state.value.apiKeyConfigured shouldBe true
        keyStore.get() shouldBe "AIzaSyTestKeyValue"
    }

    @Test
    fun `setApiKey trims whitespace`() = runTest {
        subject.setApiKey("   AIzaSyKey   ")
        keyStore.get() shouldBe "AIzaSyKey"
    }

    @Test
    fun `clearApiKey removes the stored key and updates state`() = runTest {
        subject.setApiKey("AIzaSyKey")
        subject.clearApiKey()
        subject.state.value.apiKeyConfigured shouldBe false
    }

    @Test
    fun `setDeliveryMode persists`() = runTest {
        subject.setDeliveryMode(DeliveryMode.NOTIFICATION_AND_TTS)
        subject.state.value.deliveryMode shouldBe DeliveryMode.NOTIFICATION_AND_TTS
        settingsRepository.preferences.first().deliveryMode shouldBe DeliveryMode.NOTIFICATION_AND_TTS
    }

    @Test
    fun `setTemperatureUnit and setDistanceUnit persist independently`() = runTest {
        subject.setTemperatureUnit(TemperatureUnit.FAHRENHEIT)
        subject.setDistanceUnit(DistanceUnit.MILES)

        val state = subject.state.value
        state.temperatureUnit shouldBe TemperatureUnit.FAHRENHEIT
        state.distanceUnit shouldBe DistanceUnit.MILES
    }
}

/** Pass-through AEAD: encrypt/decrypt are identity. Sufficient for round-trip tests. */
private object IdentityFakeAead : Aead {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray = plaintext
    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray = ciphertext
}
