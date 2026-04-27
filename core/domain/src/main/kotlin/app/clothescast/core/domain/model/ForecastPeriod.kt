package app.clothescast.core.domain.model

/**
 * Which slice of the day the insight is talking about.
 *
 *  - [TODAY]    — the morning forecast that fires at the user's scheduled wake-up time
 *    (default 07:00). Covers the daytime window 07:00–19:00.
 *  - [TONIGHT]  — the evening forecast that fires at the user's evening time (default
 *    19:00). Covers the overnight window 19:00–07:00. The summary leads with
 *    "Tonight will be …" and the wardrobe / outfit reflect the overnight low.
 */
enum class ForecastPeriod { TODAY, TONIGHT }
