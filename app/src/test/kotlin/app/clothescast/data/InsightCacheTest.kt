package app.clothescast.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.clothescast.core.domain.model.AlertClause
import app.clothescast.core.domain.model.BandClause
import app.clothescast.core.domain.model.CalendarTieInClause
import app.clothescast.core.domain.model.DeltaClause
import app.clothescast.core.domain.model.ForecastPeriod
import app.clothescast.core.domain.model.HourlyForecast
import app.clothescast.core.domain.model.Insight
import app.clothescast.core.domain.model.InsightSummary
import app.clothescast.core.domain.model.PrecipClause
import app.clothescast.core.domain.model.TemperatureBand
import app.clothescast.core.domain.model.WardrobeClause
import app.clothescast.core.domain.model.WeatherCondition
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class InsightCacheTest {
    @TempDir lateinit var tempDir: Path

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var subject: InsightCache

    private val today = LocalDate.of(2026, 4, 25)
    private val now = Instant.parse("2026-04-25T07:00:00Z")

    private val sampleSummary = InsightSummary(
        period = ForecastPeriod.TODAY,
        band = BandClause(TemperatureBand.COOL, TemperatureBand.MILD),
        delta = DeltaClause(4, DeltaClause.Direction.COOLER),
        wardrobe = WardrobeClause(listOf("jumper", "umbrella")),
    )

    private val sample = Insight(
        summary = sampleSummary,
        recommendedItems = listOf("jumper", "umbrella"),
        generatedAt = now,
        forDate = today,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(TestCoroutineScheduler()))
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempDir.toFile(), "cache.preferences_pb") },
        )
        subject = InsightCache(dataStore)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `latest is null when nothing stored`() = runTest {
        subject.latest.first() shouldBe null
    }

    @Test
    fun `store then latest round-trips all fields`() = runTest {
        subject.store(sample)

        val read = subject.latest.first()
        read shouldBe sample
    }

    @Test
    fun `forToday returns the cached insight when forDate matches`() = runTest {
        subject.store(sample)

        subject.forToday(today) shouldBe sample
    }

    @Test
    fun `forToday returns null when the cached insight is for a different day`() = runTest {
        subject.store(sample)

        subject.forToday(today.plusDays(1)) shouldBe null
    }

    @Test
    fun `clear removes the stored insight`() = runTest {
        subject.store(sample)
        subject.clear()

        subject.latest.first() shouldBe null
    }

    @Test
    fun `corrupt JSON in the slot maps to null rather than crashing`() = runTest {
        dataStore.edit {
            it[stringPreferencesKey("latest_insight_v3")] = "{not valid json"
        }

        subject.latest.first() shouldBe null
    }

    @Test
    fun `pre-v3 prose-summary payloads are dropped rather than crashing the cache`() = runTest {
        // Older app versions stored `summary` as a rendered string. The schema bump
        // to v3 carries a structured InsightSummary instead, so a v2 payload no
        // longer deserialises and the slot drops to null. The next worker run
        // regenerates on the new key.
        val v2Json = """
            {
              "summary": "Cooler than yesterday — bring a jumper.",
              "recommendedItems": ["jumper", "umbrella"],
              "generatedAtEpochMillis": ${now.toEpochMilli()},
              "forDateEpochDays": ${today.toEpochDay()}
            }
        """.trimIndent()
        dataStore.edit {
            // v3 key, v2-shaped payload — the decoder fails on the `summary` field
            // and the runCatching in the cache returns null.
            it[stringPreferencesKey("latest_insight_v3")] = v2Json
        }

        subject.latest.first() shouldBe null
    }

    @Test
    fun `hourly entries round-trip through the cache`() = runTest {
        val hourly = listOf(
            HourlyForecast(
                time = LocalTime.of(7, 0),
                temperatureC = 9.5,
                feelsLikeC = 7.2,
                precipitationProbabilityPct = 10.0,
                condition = WeatherCondition.PARTLY_CLOUDY,
            ),
            HourlyForecast(
                time = LocalTime.of(13, 0),
                temperatureC = 14.0,
                feelsLikeC = 13.0,
                precipitationProbabilityPct = 60.0,
                condition = WeatherCondition.RAIN,
            ),
        )
        val withHourly = sample.copy(hourly = hourly)

        subject.store(withHourly)

        subject.latest.first() shouldBe withHourly
    }

    @Test
    fun `every clause type round-trips through the cache`() = runTest {
        val full = sample.copy(
            summary = InsightSummary(
                period = ForecastPeriod.TODAY,
                band = BandClause(TemperatureBand.FREEZING, TemperatureBand.HOT),
                alert = AlertClause("Tornado Warning"),
                delta = DeltaClause(8, DeltaClause.Direction.WARMER),
                wardrobe = WardrobeClause(listOf("jumper", "jacket", "shorts", "umbrella")),
                precip = PrecipClause(WeatherCondition.RAIN, LocalTime.of(15, 0)),
                calendarTieIn = CalendarTieInClause("umbrella", LocalTime.of(15, 0), "park run"),
            ),
        )

        subject.store(full)

        subject.latest.first() shouldBe full
    }
}
