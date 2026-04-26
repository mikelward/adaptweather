package app.clothescast.core.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class InsightTest {

    private val base = Insight(
        summary = "Today will be cool to mild. Wear a jumper and jacket.",
        recommendedItems = listOf("jumper", "jacket"),
        generatedAt = Instant.parse("2026-04-25T07:00:00Z"),
        forDate = LocalDate.of(2026, 4, 25),
    )

    @Test
    fun `spokenText returns the summary verbatim`() {
        // The summary already includes the wardrobe sentence, so spokenText must not
        // append a second "Recommended: ..." block — that produced the two-level
        // hierarchy we deliberately removed.
        base.spokenText() shouldBe base.summary
    }

    @Test
    fun `spokenText still returns the summary when no items were recommended`() {
        base.copy(
            summary = "Today will be mild.",
            recommendedItems = emptyList(),
        ).spokenText() shouldBe "Today will be mild."
    }
}
