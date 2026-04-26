package app.clothescast.core.data.weather

import app.clothescast.core.domain.model.ConfidenceInfo
import app.clothescast.core.domain.model.ForecastConfidence
import app.clothescast.core.domain.model.Location
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.URLProtocol
import io.ktor.http.path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Fetches today's apparent-max temperature and peak precipitation probability from
 * several Open-Meteo models in parallel, computes the cross-model spread, and maps
 * to a [ConfidenceInfo]. When the major models agree, we surface "High confidence";
 * when they disagree, "Low confidence — forecasts disagree".
 *
 * Best-effort: any failure (network, model unavailable, parse error) falls through
 * to a null result. The user still gets the daily forecast — just no confidence
 * badge.
 *
 * Each model call requests only the two daily fields we need, so the responses are
 * tiny.
 */
internal class MultiModelConfidenceFetcher(
    private val httpClient: HttpClient,
    private val models: List<String> = DEFAULT_MODELS,
) {
    suspend fun fetch(location: Location): ConfidenceInfo? = try {
        coroutineScope {
            val results = models.map { model ->
                async { runCatching { fetchOne(location, model) }.getOrNull()?.let { model to it } }
            }.awaitAll().filterNotNull()

            // Need at least two models to compute a spread.
            if (results.size < 2) null else compute(results)
        }
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Throwable) {
        null
    }

    private suspend fun fetchOne(location: Location, model: String): ModelDailyValues? {
        val response: ConfidenceResponse = httpClient.get {
            url {
                protocol = URLProtocol.HTTPS
                host = OPEN_METEO_HOST
                path("v1", "forecast")
            }
            parameter("latitude", location.latitude)
            parameter("longitude", location.longitude)
            parameter("forecast_days", 1)
            parameter("timezone", "auto")
            parameter("daily", "apparent_temperature_max,precipitation_probability_max")
            parameter("models", model)
        }.body()

        // Index 0 is today (no past_days requested). Empty array or null entry → unusable.
        val tempMax = response.daily.feelsLikeMax.firstOrNull() ?: return null
        val precipMax = response.daily.precipitationProbabilityMax.firstOrNull()?.toDouble() ?: return null
        return ModelDailyValues(tempMax, precipMax)
    }

    private fun compute(results: List<Pair<String, ModelDailyValues>>): ConfidenceInfo {
        val temps = results.map { it.second.tempMaxC }
        val precips = results.map { it.second.precipMaxPp }
        val tempSpread = temps.max() - temps.min()
        val precipSpread = precips.max() - precips.min()

        val level = when {
            tempSpread <= TEMP_HIGH_AGREEMENT_C && precipSpread <= PRECIP_HIGH_AGREEMENT_PP ->
                ForecastConfidence.HIGH
            tempSpread <= TEMP_MEDIUM_AGREEMENT_C && precipSpread <= PRECIP_MEDIUM_AGREEMENT_PP ->
                ForecastConfidence.MEDIUM
            else -> ForecastConfidence.LOW
        }

        return ConfidenceInfo(
            level = level,
            tempSpreadC = tempSpread,
            precipSpreadPp = precipSpread,
            modelsConsulted = results.map { it.first },
        )
    }

    private data class ModelDailyValues(val tempMaxC: Double, val precipMaxPp: Double)

    companion object {
        // Three models with global coverage so the spread is meaningful regardless of
        // where the user is. Could be made user-tunable later (MODELS.md idea).
        val DEFAULT_MODELS = listOf("ecmwf_ifs04", "gfs_seamless", "icon_seamless")

        // Thresholds are deliberate first-pass guesses; refine with real data.
        // Both temp and precip have to clear the bar for HIGH; either dropping
        // moves us down a tier.
        internal const val TEMP_HIGH_AGREEMENT_C = 1.5
        internal const val TEMP_MEDIUM_AGREEMENT_C = 3.0
        internal const val PRECIP_HIGH_AGREEMENT_PP = 15.0
        internal const val PRECIP_MEDIUM_AGREEMENT_PP = 30.0
    }
}

@Serializable
private data class ConfidenceResponse(
    @SerialName("daily") val daily: ConfidenceDaily,
)

@Serializable
private data class ConfidenceDaily(
    @SerialName("apparent_temperature_max") val feelsLikeMax: List<Double?>,
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int?>,
)
