package app.clothescast.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.HourlyForecast
import app.clothescast.core.domain.model.Insight
import app.clothescast.core.domain.model.OutfitSuggestion
import app.clothescast.core.domain.model.WeatherCondition
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
    /**
     * The most recent insight written to the cache, regardless of period. The Today
     * screen surfaces this so opening the app after the tonight alarm fired shows
     * the tonight insight (the freshest read of what the user is about to walk
     * into), while opening the app at noon still shows the morning insight.
     *
     * Per-period storage lives in [latestForPeriod] / [forPeriodToday] so the
     * worker can avoid clobbering the morning insight with the tonight one.
     */
    val latest: Flow<Insight?> = dataStore.data.map { prefs ->
        listOfNotNull(
            prefs.readSlot(TODAY_INSIGHT_JSON),
            prefs.readSlot(TONIGHT_INSIGHT_JSON),
        ).maxByOrNull { it.generatedAt }
    }

    fun latestForPeriod(period: ForecastPeriod): Flow<Insight?> = dataStore.data.map { prefs ->
        prefs.readSlot(keyFor(period))
    }

    suspend fun store(insight: Insight) {
        dataStore.edit { it[keyFor(insight.period)] = json.encodeToString(insight.toDto()) }
    }

    suspend fun forToday(today: LocalDate): Insight? = forPeriodToday(today, ForecastPeriod.TODAY)

    suspend fun forPeriodToday(today: LocalDate, period: ForecastPeriod): Insight? {
        val cached = latestForPeriod(period).first() ?: return null
        return cached.takeIf { it.forDate == today }
    }

    suspend fun clear() {
        dataStore.edit {
            it.remove(TODAY_INSIGHT_JSON)
            it.remove(TONIGHT_INSIGHT_JSON)
        }
    }

    private fun Preferences.readSlot(key: androidx.datastore.preferences.core.Preferences.Key<String>): Insight? {
        val raw = this[key] ?: return null
        return runCatching { json.decodeFromString<InsightDto>(raw).toDomain() }.getOrNull()
    }

    private fun keyFor(period: ForecastPeriod): androidx.datastore.preferences.core.Preferences.Key<String> =
        when (period) {
            ForecastPeriod.TODAY -> TODAY_INSIGHT_JSON
            ForecastPeriod.TONIGHT -> TONIGHT_INSIGHT_JSON
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
        val period: String = ForecastPeriod.TODAY.name,
        val hasEvents: Boolean = false,
    ) {
        fun toDomain(): Insight = Insight(
            summary = summary,
            recommendedItems = recommendedItems,
            generatedAt = Instant.ofEpochMilli(generatedAtEpochMillis),
            forDate = LocalDate.ofEpochDay(forDateEpochDays),
            hourly = hourly.map { it.toDomain() },
            outfit = outfit?.toDomain(),
            period = runCatching { ForecastPeriod.valueOf(period) }.getOrDefault(ForecastPeriod.TODAY),
            hasEvents = hasEvents,
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
        period = period.name,
        hasEvents = hasEvents,
    )

    private fun HourlyForecast.toDto(): HourlyDto = HourlyDto(
        secondOfDay = time.toSecondOfDay(),
        temperatureC = temperatureC,
        feelsLikeC = feelsLikeC,
        precipitationProbabilityPct = precipitationProbabilityPct,
        condition = condition.name,
    )

    companion object {
        // Bumped from `latest_insight_v1` when the daily insight moved from a Gemini
        // text call to the deterministic local renderer. Pre-bump entries can carry
        // truncated LLM output (`"Today will be"` with nothing after — the band
        // sentence chopped at maxOutputTokens) and would otherwise stick around
        // until the user crossed midnight, since the worker reuses any cached
        // entry that matches `forToday`.
        // The today slot kept the v2 key so an in-place upgrade redelivers the
        // existing cached morning insight; tonight is brand-new so it gets v1.
        private val TODAY_INSIGHT_JSON = stringPreferencesKey("latest_insight_v2")
        private val TONIGHT_INSIGHT_JSON = stringPreferencesKey("latest_tonight_insight_v1")

        fun create(context: Context): InsightCache = InsightCache(context.insightDataStore)
    }
}

private val Context.insightDataStore: DataStore<Preferences> by preferencesDataStore(name = "insight_cache")
