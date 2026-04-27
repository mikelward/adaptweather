package app.clothescast.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.clothescast.core.domain.model.AlertClause
import app.clothescast.core.domain.model.BandClause
import app.clothescast.core.domain.model.CalendarTieInClause
import app.clothescast.core.domain.model.DeltaClause
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.HourlyForecast
import app.clothescast.core.domain.model.Insight
import app.clothescast.core.domain.model.InsightSummary
import app.clothescast.core.domain.model.NextPeriodClause
import app.clothescast.core.domain.model.OutfitSuggestion
import app.clothescast.core.domain.model.PrecipClause
import app.clothescast.core.domain.model.TemperatureBand
import app.clothescast.core.domain.model.WardrobeClause
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
 * Persists the most recently generated [Insight] so the daily worker can avoid
 * regenerating twice for the same day. The cache key is the insight's `forDate`
 * (LocalDate), which means two runs on the same calendar day reuse the same
 * insight even across process kills, reboots, or "Fire insight now" debug taps.
 *
 * The cache stores the structured [InsightSummary] (each clause as typed data)
 * rather than rendered prose, so a Region-setting change re-renders the cached
 * insight in the new locale without re-fetching the forecast.
 *
 * The cache is intentionally a single slot per period — there's no historical
 * browsing planned, and bounding storage at 2 entries (TODAY + TONIGHT) keeps the
 * cost of corruption (deserialization failure → drop and regenerate) trivial.
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
        val summary: InsightSummaryDto,
        val recommendedItems: List<String>,
        val generatedAtEpochMillis: Long,
        val forDateEpochDays: Long,
        val hourly: List<HourlyDto> = emptyList(),
        val outfit: OutfitDto? = null,
        val nextOutfit: OutfitDto? = null,
        val period: String = ForecastPeriod.TODAY.name,
        val hasEvents: Boolean = false,
    ) {
        fun toDomain(): Insight = Insight(
            summary = summary.toDomain(),
            recommendedItems = recommendedItems,
            generatedAt = Instant.ofEpochMilli(generatedAtEpochMillis),
            forDate = LocalDate.ofEpochDay(forDateEpochDays),
            hourly = hourly.map { it.toDomain() },
            outfit = outfit?.toDomain(),
            nextOutfit = nextOutfit?.toDomain(),
            period = runCatching { ForecastPeriod.valueOf(period) }.getOrDefault(ForecastPeriod.TODAY),
            hasEvents = hasEvents,
        )
    }

    @Serializable
    private data class InsightSummaryDto(
        val period: String,
        val band: BandDto,
        val alert: AlertDto? = null,
        val delta: DeltaDto? = null,
        val wardrobe: WardrobeDto? = null,
        val precip: PrecipDto? = null,
        val calendarTieIn: CalendarTieInDto? = null,
        val nextPeriod: NextPeriodDto? = null,
    ) {
        fun toDomain(): InsightSummary = InsightSummary(
            period = runCatching { ForecastPeriod.valueOf(period) }.getOrDefault(ForecastPeriod.TODAY),
            band = band.toDomain(),
            alert = alert?.toDomain(),
            delta = delta?.toDomain(),
            wardrobe = wardrobe?.toDomain(),
            precip = precip?.toDomain(),
            calendarTieIn = calendarTieIn?.toDomain(),
            nextPeriod = nextPeriod?.toDomain(),
        )
    }

    @Serializable
    private data class BandDto(val low: String, val high: String) {
        fun toDomain(): BandClause = BandClause(
            low = runCatching { TemperatureBand.valueOf(low) }.getOrDefault(TemperatureBand.MILD),
            high = runCatching { TemperatureBand.valueOf(high) }.getOrDefault(TemperatureBand.MILD),
        )
    }

    @Serializable
    private data class AlertDto(val event: String) {
        fun toDomain(): AlertClause = AlertClause(event)
    }

    @Serializable
    private data class DeltaDto(val degrees: Int, val direction: String) {
        fun toDomain(): DeltaClause = DeltaClause(
            degrees = degrees,
            direction = runCatching { DeltaClause.Direction.valueOf(direction) }
                .getOrDefault(DeltaClause.Direction.WARMER),
        )
    }

    @Serializable
    private data class WardrobeDto(val items: List<String>) {
        fun toDomain(): WardrobeClause = WardrobeClause(items)
    }

    @Serializable
    private data class PrecipDto(val condition: String, val secondOfDay: Int) {
        fun toDomain(): PrecipClause = PrecipClause(
            condition = runCatching { WeatherCondition.valueOf(condition) }
                .getOrDefault(WeatherCondition.UNKNOWN),
            time = LocalTime.ofSecondOfDay(secondOfDay.toLong()),
        )
    }

    @Serializable
    private data class CalendarTieInDto(
        val item: String,
        val secondOfDay: Int,
        val title: String,
    ) {
        fun toDomain(): CalendarTieInClause = CalendarTieInClause(
            item = item,
            time = LocalTime.ofSecondOfDay(secondOfDay.toLong()),
            title = title,
        )
    }

    @Serializable
    private data class NextPeriodDto(
        val precip: PrecipDto? = null,
        val isColder: Boolean = false,
    ) {
        fun toDomain(): NextPeriodClause = NextPeriodClause(
            precip = precip?.toDomain(),
            isColder = isColder,
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
        summary = summary.toDto(),
        recommendedItems = recommendedItems,
        generatedAtEpochMillis = generatedAt.toEpochMilli(),
        forDateEpochDays = forDate.toEpochDay(),
        hourly = hourly.map { it.toDto() },
        outfit = outfit?.let { OutfitDto(it.top.name, it.bottom.name) },
        nextOutfit = nextOutfit?.let { OutfitDto(it.top.name, it.bottom.name) },
        period = period.name,
        hasEvents = hasEvents,
    )

    private fun InsightSummary.toDto(): InsightSummaryDto = InsightSummaryDto(
        period = period.name,
        band = BandDto(band.low.name, band.high.name),
        alert = alert?.let { AlertDto(it.event) },
        delta = delta?.let { DeltaDto(it.degrees, it.direction.name) },
        wardrobe = wardrobe?.let { WardrobeDto(it.items) },
        precip = precip?.let { PrecipDto(it.condition.name, it.time.toSecondOfDay()) },
        calendarTieIn = calendarTieIn?.let {
            CalendarTieInDto(it.item, it.time.toSecondOfDay(), it.title)
        },
        nextPeriod = nextPeriod?.let {
            NextPeriodDto(
                precip = it.precip?.let { p -> PrecipDto(p.condition.name, p.time.toSecondOfDay()) },
                isColder = it.isColder,
            )
        },
    )

    private fun HourlyForecast.toDto(): HourlyDto = HourlyDto(
        secondOfDay = time.toSecondOfDay(),
        temperatureC = temperatureC,
        feelsLikeC = feelsLikeC,
        precipitationProbabilityPct = precipitationProbabilityPct,
        condition = condition.name,
    )

    companion object {
        // Bumped from `latest_insight_v2` when the cached `summary` flipped from a
        // rendered prose string to a structured [InsightSummary]. Old prose-summary
        // payloads can't deserialize against the new schema and are dropped on first
        // read, regenerating on the next worker run.
        // Tonight is bumped from `latest_tonight_insight_v1` for the same reason.
        private val TODAY_INSIGHT_JSON = stringPreferencesKey("latest_insight_v3")
        private val TONIGHT_INSIGHT_JSON = stringPreferencesKey("latest_tonight_insight_v2")

        fun create(context: Context): InsightCache = InsightCache(context.insightDataStore)
    }
}

private val Context.insightDataStore: DataStore<Preferences> by preferencesDataStore(name = "insight_cache")
