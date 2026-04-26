package app.adaptweather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.adaptweather.core.data.insight.KeyProvider
import app.adaptweather.core.data.insight.MissingApiKeyException
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Base64

/**
 * Encrypted on-device storage of the user's API keys.
 *
 * Each key is encrypted with a Tink AEAD primitive whose keyset is itself encrypted
 * by an Android-Keystore master key — so the secret material never appears in
 * SharedPreferences in plaintext. Ciphertext is persisted in DataStore Preferences
 * under per-provider preference keys (Gemini in one slot, OpenAI in another).
 *
 * EncryptedSharedPreferences is deprecated as of androidx.security:security-crypto
 * 1.1.0-alpha07 — Tink + DataStore is its modern replacement.
 *
 * Constructor takes an [Aead] and a [DataStore] so the class is unit-testable with
 * a fake AEAD and a test DataStore (e.g. temp-file-backed via PreferenceDataStoreFactory).
 * Production callers use [SecureKeyStore.create].
 *
 * `get()` reports any decode or decrypt failure as [MissingApiKeyException] and clears
 * the stored value. This handles the Keystore master key rotating (factory reset,
 * device-to-device transfer that doesn't preserve hardware-backed keys, etc.) by asking
 * the user to re-enter their key, rather than looping on a corrupt ciphertext.
 *
 * `SecureKeyStore` itself implements [KeyProvider] — `get()` returns the Gemini key,
 * matching the contract `GeminiTtsClient` consumes. The OpenAI key is exposed through
 * [openAiKeyProvider].
 */
class SecureKeyStore(
    private val aead: Aead,
    private val dataStore: DataStore<Preferences>,
) : KeyProvider {

    override suspend fun get(): String = read(GEMINI_PREF_KEY, GEMINI_AAD, "Gemini")

    suspend fun set(key: String) = write(GEMINI_PREF_KEY, GEMINI_AAD, key)

    suspend fun clear() = remove(GEMINI_PREF_KEY)

    suspend fun getOpenAi(): String = read(OPENAI_PREF_KEY, OPENAI_AAD, "OpenAI")

    suspend fun setOpenAi(key: String) = write(OPENAI_PREF_KEY, OPENAI_AAD, key)

    suspend fun clearOpenAi() = remove(OPENAI_PREF_KEY)

    /**
     * KeyProvider view onto the OpenAI key. Used to wire `OpenAITtsClient` while
     * keeping the underlying SecureKeyStore as the single source of truth for
     * encrypted on-device storage.
     */
    val openAiKeyProvider: KeyProvider = object : KeyProvider {
        override suspend fun get(): String = getOpenAi()
    }

    private suspend fun read(prefKey: Preferences.Key<String>, aad: ByteArray, provider: String): String {
        val ciphertextB64 = dataStore.data.map { it[prefKey] }.first()
            ?: throw MissingApiKeyException(provider)
        return try {
            val ciphertext = Base64.getDecoder().decode(ciphertextB64)
            aead.decrypt(ciphertext, aad).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            // Corrupt ciphertext or unrecoverable Keystore state — drop the bad value so
            // the next attempt prompts the user to re-enter their key cleanly.
            remove(prefKey)
            throw MissingApiKeyException(provider)
        }
    }

    private suspend fun write(prefKey: Preferences.Key<String>, aad: ByteArray, key: String) {
        val ciphertext = aead.encrypt(key.toByteArray(Charsets.UTF_8), aad)
        val ciphertextB64 = Base64.getEncoder().encodeToString(ciphertext)
        dataStore.edit { it[prefKey] = ciphertextB64 }
    }

    private suspend fun remove(prefKey: Preferences.Key<String>) {
        dataStore.edit { it.remove(prefKey) }
    }

    companion object {
        private val GEMINI_PREF_KEY = stringPreferencesKey("gemini_api_key_v1")
        private val GEMINI_AAD = "adaptweather:gemini_api_key:v1".toByteArray(Charsets.UTF_8)

        private val OPENAI_PREF_KEY = stringPreferencesKey("openai_api_key_v1")
        private val OPENAI_AAD = "adaptweather:openai_api_key:v1".toByteArray(Charsets.UTF_8)

        private const val MASTER_KEY_URI = "android-keystore://adaptweather_master_key"
        private const val KEYSET_PREFS = "adaptweather_master_prefs"
        private const val KEYSET_NAME = "adaptweather_master_keyset"

        fun create(context: Context): SecureKeyStore {
            AeadConfig.register()
            val aead: Aead = AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
                .getPrimitive(Aead::class.java)
            return SecureKeyStore(aead, context.secureKeyDataStore)
        }
    }
}

private val Context.secureKeyDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_key_store")
