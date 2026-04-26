package app.adaptweather.core.data.insight

/**
 * Source of the user's Gemini API key. Implemented by the storage layer in :app
 * (Tink-encrypted DataStore for v1's BYOK path) and faked in tests.
 *
 * Suspend so the implementation may read encrypted storage off the main thread
 * without exposing thread-affinity to callers.
 */
interface KeyProvider {
    suspend fun get(): String
}

/** Thrown when no API key has been configured by the user. */
class MissingApiKeyException(provider: String = "Gemini") :
    IllegalStateException("$provider API key has not been configured")
