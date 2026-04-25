package com.adaptweather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adaptweather.core.domain.model.Insight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/**
 * Persists the most recently generated [Insight] so the daily worker can avoid hitting
 * Gemini twice for the same day. The cache key is the insight's `forDate` (LocalDate),
 * which means two runs on the same calendar day reuse the same insight even across
 * process kills, reboots, or "Fire insight now" debug taps.
 *
 * The cache is intentionally a single slot — there's no historical browsing planned,
 * and bounding storage at 1 entry keeps the cost of corruption (deserialization
 * failure → drop and regenerate) trivial.
 */
class InsightCache(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    val latest: Flow<Insight?> = dataStore.data.map { prefs ->
        prefs[INSIGHT_JSON]?.let { raw ->
            runCatching { json.decodeFromString<InsightDto>(raw).toDomain() }.getOrNull()
        }
    }

    suspend fun store(insight: Insight) {
        dataStore.edit { it[INSIGHT_JSON] = json.encodeToString(insight.toDto()) }
    }

    suspend fun forToday(today: LocalDate): Insight? {
        val cached = latest.first() ?: return null
        return cached.takeIf { it.forDate == today }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(INSIGHT_JSON) }
    }

    @Serializable
    private data class InsightDto(
        val summary: String,
        val recommendedItems: List<String>,
        val generatedAtEpochMillis: Long,
        val forDateEpochDays: Long,
    ) {
        fun toDomain(): Insight = Insight(
            summary = summary,
            recommendedItems = recommendedItems,
            generatedAt = Instant.ofEpochMilli(generatedAtEpochMillis),
            forDate = LocalDate.ofEpochDay(forDateEpochDays),
        )
    }

    private fun Insight.toDto(): InsightDto = InsightDto(
        summary = summary,
        recommendedItems = recommendedItems,
        generatedAtEpochMillis = generatedAt.toEpochMilli(),
        forDateEpochDays = forDate.toEpochDay(),
    )

    companion object {
        private val INSIGHT_JSON = stringPreferencesKey("latest_insight_v1")

        fun create(context: Context): InsightCache = InsightCache(context.insightDataStore)
    }
}

private val Context.insightDataStore: DataStore<Preferences> by preferencesDataStore(name = "insight_cache")
