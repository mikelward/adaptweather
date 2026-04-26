package app.adaptweather.core.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class InsightTest {

    private val base = Insight(
        summary = "It will be 4° warmer today.",
        recommendedItems = emptyList(),
        generatedAt = Instant.parse("2026-04-25T07:00:00Z"),
        forDate = LocalDate.of(2026, 4, 25),
    )

    @Test
    fun `spokenText returns just the summary when no items`() {
        base.spokenText() shouldBe "It will be 4° warmer today."
    }

    @Test
    fun `spokenText appends a single item with proper separator`() {
        base.copy(recommendedItems = listOf("jumper")).spokenText() shouldBe
            "It will be 4° warmer today. Recommended: jumper."
    }

    @Test
    fun `spokenText joins two items with and`() {
        base.copy(recommendedItems = listOf("jumper", "umbrella")).spokenText() shouldBe
            "It will be 4° warmer today. Recommended: jumper and umbrella."
    }

    @Test
    fun `spokenText oxford-joins three or more items`() {
        base.copy(recommendedItems = listOf("jumper", "scarf", "umbrella")).spokenText() shouldBe
            "It will be 4° warmer today. Recommended: jumper, scarf, and umbrella."
    }

    @Test
    fun `spokenText uses single space when summary already ends in punctuation`() {
        // Summary already ending in a period should not get ". ." appended.
        base.copy(
            summary = "Bring a brolly!",
            recommendedItems = listOf("umbrella"),
        ).spokenText() shouldBe "Bring a brolly! Recommended: umbrella."
    }

    @Test
    fun `spokenText adds period+space when summary lacks terminal punctuation`() {
        base.copy(
            summary = "Cooler today",
            recommendedItems = listOf("jumper"),
        ).spokenText() shouldBe "Cooler today. Recommended: jumper."
    }
}
