package com.adaptweather.core.domain.model

/**
 * Cross-model agreement signal. Open-Meteo aggregates several national weather
 * services (ECMWF, DWD ICON, NOAA GFS, Météo-France, …) under one API; the same
 * endpoint accepts `&models=…` and returns each requested model side-by-side.
 *
 * When the major models *disagree* — say, one says 18 °C and another says 23 °C
 * — the forecast is least trustworthy. Surfacing that as a confidence level on
 * Today is actionable information we get for free; the user has a cue to check
 * again later or hedge their wardrobe choice.
 *
 * Idea sketched in [docs/MODELS.md](../../../../../../../../docs/MODELS.md) #1.
 */
data class ConfidenceInfo(
    val level: ForecastConfidence,
    /** Max - min of today's apparent-max temperature across the consulted models, °C. */
    val tempSpreadC: Double,
    /** Max - min of today's peak precipitation probability across the consulted models, percentage points. */
    val precipSpreadPp: Double,
    /** Open-Meteo model ids that contributed (e.g. `ecmwf_ifs04`, `gfs_seamless`). */
    val modelsConsulted: List<String>,
)

enum class ForecastConfidence { HIGH, MEDIUM, LOW }
