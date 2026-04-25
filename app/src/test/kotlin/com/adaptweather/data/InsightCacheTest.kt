package com.adaptweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.adaptweather.core.domain.model.Insight
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

@OptIn(ExperimentalCoroutinesApi::class)
class InsightCacheTest {
    @TempDir lateinit var tempDir: Path

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var subject: InsightCache

    private val today = LocalDate.of(2026, 4, 25)
    private val now = Instant.parse("2026-04-25T07:00:00Z")

    private val sample = Insight(
        summary = "Cooler than yesterday — bring a jumper.",
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
            it[stringPreferencesKey("latest_insight_v1")] = "{not valid json"
        }

        subject.latest.first() shouldBe null
    }
}
