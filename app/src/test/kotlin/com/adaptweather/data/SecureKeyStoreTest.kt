package com.adaptweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.adaptweather.core.data.insight.MissingApiKeyException
import com.google.crypto.tink.Aead
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SecureKeyStoreTest {
    @TempDir lateinit var tempDir: Path

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var subject: SecureKeyStore

    @BeforeEach
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir.toFile(), "secure_key.preferences_pb") },
        )
        subject = SecureKeyStore(aead = ReversibleFakeAead, dataStore = dataStore)
    }

    @Test
    fun `set then get returns the same key`() = runTest {
        subject.set("AIzaSyExampleKeyValue123")

        subject.get() shouldBe "AIzaSyExampleKeyValue123"
    }

    @Test
    fun `get without prior set throws MissingApiKeyException`() = runTest {
        shouldThrow<MissingApiKeyException> { subject.get() }
    }

    @Test
    fun `clear removes the key`() = runTest {
        subject.set("AIzaSyExampleKeyValue123")
        subject.clear()

        shouldThrow<MissingApiKeyException> { subject.get() }
    }

    @Test
    fun `set replaces the previous key`() = runTest {
        subject.set("first")
        subject.set("second")

        subject.get() shouldBe "second"
    }

    @Test
    fun `stored ciphertext is not the plaintext key`() = runTest {
        val plaintext = "AIzaSyVerySecretValue"
        subject.set(plaintext)

        val storedB64 = dataStore.data.first().asMap().values.single() as String
        // ReversibleFakeAead reverses the bytes, so the stored value must be a
        // base64 string that decodes to the reversed plaintext — confirming we
        // pass through the AEAD before persisting.
        storedB64 shouldBe java.util.Base64.getEncoder()
            .encodeToString(plaintext.toByteArray(Charsets.UTF_8).reversedArray())
    }
}

/**
 * Test-only AEAD: encrypt = reverse the bytes; decrypt = reverse again.
 * Round-trip semantics are sufficient for SecureKeyStore's contract; we don't
 * test cryptographic strength here (Tink's own tests cover that).
 */
private object ReversibleFakeAead : Aead {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray?): ByteArray =
        plaintext.reversedArray()

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray?): ByteArray =
        ciphertext.reversedArray()
}
