package app.adaptweather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.adaptweather.core.domain.model.HourlyForecast
import app.adaptweather.core.domain.model.Insight
import app.adaptweather.core.domain.model.OutfitSuggestion
import app.adaptweather.core.domain.model.WeatherCondition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

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
        // Default keeps v1 cached payloads readable: older slots without hourly will
        // deserialize as an empty list, the chart hides itself, and the next worker
        // run rewrites with hourly populated.
        val hourly: List<HourlyDto> = emptyList(),
        val outfit: OutfitDto? = null,
    ) {
        fun toDomain(): Insight = Insight(
            summary = summary,
            recommendedItems = recommendedItems,
            generatedAt = Instant.ofEpochMilli(generatedAtEpochMillis),
            forDate = LocalDate.ofEpochDay(forDateEpochDays),
            hourly = hourly.map { it.toDomain() },
            outfit = outfit?.toDomain(),
        )
    }

    @Serializable
    private data class OutfitDto(val top: String, val bottom: String) {
        fun toDomain(): OutfitSuggestion? {
            val t = runCatching { OutfitSuggestion.Top.valueOf(top) }.getOrNull() ?: return null
            val b = runCatching { OutfitSuggestion.Bottom.valueOf(bottom) }.getOrNull() ?: return null
            return OutfitSuggestion(t, b)
        }
    }

    @Serializable
    private data class HourlyDto(
        val secondOfDay: Int,
        val temperatureC: Double,
        val feelsLikeC: Double,
        val precipitationProbabilityPct: Double,
        val condition: String,
    ) {
        fun toDomain(): HourlyForecast = HourlyForecast(
            time = LocalTime.ofSecondOfDay(secondOfDay.toLong()),
            temperatureC = temperatureC,
            feelsLikeC = feelsLikeC,
            precipitationProbabilityPct = precipitationProbabilityPct,
            condition = runCatching { WeatherCondition.valueOf(condition) }
                .getOrDefault(WeatherCondition.UNKNOWN),
        )
    }

    private fun Insight.toDto(): InsightDto = InsightDto(
        summary = summary,
        recommendedItems = recommendedItems,
        generatedAtEpochMillis = generatedAt.toEpochMilli(),
        forDateEpochDays = forDate.toEpochDay(),
        hourly = hourly.map { it.toDto() },
        outfit = outfit?.let { OutfitDto(it.top.name, it.bottom.name) },
    )

    private fun HourlyForecast.toDto(): HourlyDto = HourlyDto(
        secondOfDay = time.toSecondOfDay(),
        temperatureC = temperatureC,
        feelsLikeC = feelsLikeC,
        precipitationProbabilityPct = precipitationProbabilityPct,
        condition = condition.name,
    )

    companion object {
        private val INSIGHT_JSON = stringPreferencesKey("latest_insight_v1")

        fun create(context: Context): InsightCache = InsightCache(context.insightDataStore)
    }
}

private val Context.insightDataStore: DataStore<Preferences> by preferencesDataStore(name = "insight_cache")
