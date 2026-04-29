package app.clothescast.tts

import app.clothescast.core.data.tts.ElevenLabsVoiceSummary
import app.clothescast.core.domain.model.VoiceLocale
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TtsVoicesTest {

    private val american = TtsVoiceOption("a", "Alice — US", VoiceLocale.EN_US)
    private val british = TtsVoiceOption("b", "Bob — UK", VoiceLocale.EN_GB)
    private val agnostic = TtsVoiceOption("c", "Cleo — any")

    @Test
    fun `SYSTEM disables filtering`() {
        val all = listOf(american, british, agnostic)
        all.filterByVariant(VoiceLocale.SYSTEM) shouldBe all
    }

    @Test
    fun `keeps matching variant and accent-agnostic voices`() {
        val all = listOf(american, british, agnostic)
        all.filterByVariant(VoiceLocale.EN_GB)
            .shouldContainExactlyInAnyOrder(british, agnostic)
    }

    @Test
    fun `falls back to the full list when no voice matches the variant`() {
        // No EN_AU voices in this list — better to show every voice (with the
        // accent in the display name) than to leave the user with an empty picker.
        val all = listOf(american, british)
        all.filterByVariant(VoiceLocale.EN_AU) shouldBe all
    }

    @Test
    fun `accent-agnostic voices alone are kept as-is, not treated as empty`() {
        // A list of only null-locale voices isn't "empty after filter" — it's
        // a list where every voice opts in. Don't trip the fallback path.
        val all = listOf(agnostic)
        all.filterByVariant(VoiceLocale.EN_GB) shouldBe all
    }

    @Test
    fun `keepSelected appends a filtered-out selection`() {
        // The motivating case: en-GB user has previously persisted `nova`
        // (en-US), then opens the picker. Without keepSelected the dialog
        // omits nova and the button label falls back to the raw voice ID.
        val all = listOf(american, british)
        all.filterByVariant(VoiceLocale.EN_GB, keepSelected = "a")
            .shouldContainExactlyInAnyOrder(british, american)
    }

    @Test
    fun `keepSelected has no effect when the selection already matches`() {
        // Selection's already in the filtered list — no duplication.
        val all = listOf(american, british)
        all.filterByVariant(VoiceLocale.EN_GB, keepSelected = "b") shouldBe listOf(british)
    }

    @Test
    fun `keepSelected does nothing when the id isn't in the source list at all`() {
        // Stale persisted id (e.g. a voice that was removed from the curated
        // list). Drop silently — the picker can't render a row we have no
        // metadata for.
        val all = listOf(american, british)
        all.filterByVariant(VoiceLocale.EN_GB, keepSelected = "ghost") shouldBe listOf(british)
    }

    @Test
    fun `keepSelected is ignored when the empty-filter fallback fires`() {
        // No matches → fallback to full list (which already includes
        // selected). keepSelected becomes a no-op rather than re-appending.
        val all = listOf(american)
        all.filterByVariant(VoiceLocale.EN_AU, keepSelected = "a") shouldBe all
    }

    @Test
    fun `toVoiceOptions formats name plus description plus accent when all present`() {
        val mapped = listOf(
            ElevenLabsVoiceSummary(id = "v1", name = "Sarah", accent = "american", description = "warm"),
        ).toVoiceOptions()
        mapped[0].id shouldBe "v1"
        mapped[0].displayName shouldBe "Sarah — warm (american)"
        // "american" is one of the accent labels we map to a VoiceLocale
        // so the picker's locale filter narrows the refreshed list the
        // same way it narrows the curated one.
        mapped[0].locale shouldBe VoiceLocale.EN_US
    }

    @Test
    fun `toVoiceOptions falls through gracefully when labels are partial or missing`() {
        val mapped = listOf(
            ElevenLabsVoiceSummary(id = "d", name = "DescOnly", description = "calm"),
            ElevenLabsVoiceSummary(id = "a", name = "AccentOnly", accent = "british"),
            ElevenLabsVoiceSummary(id = "n", name = "Nameless"),
            // Whitespace-only labels are treated the same as null so we don't
            // render "Sarah —  ()" for sparsely-tagged clones.
            ElevenLabsVoiceSummary(id = "b", name = "Blank", accent = "  ", description = ""),
        ).toVoiceOptions()
        mapped.map { it.displayName } shouldBe listOf(
            "DescOnly — calm",
            "AccentOnly (british)",
            "Nameless",
            "Blank",
        )
    }

    @Test
    fun `toVoiceOptions maps recognised accent labels onto VoiceLocale`() {
        // Spot-check the headline English variants users are most likely to
        // filter by. "south african" exercises the multi-word lookup;
        // upper-case input exercises the lowercase normalisation.
        val mapped = listOf(
            ElevenLabsVoiceSummary("a", "A", accent = "American"),
            ElevenLabsVoiceSummary("b", "B", accent = "british"),
            ElevenLabsVoiceSummary("c", "C", accent = "australian"),
            ElevenLabsVoiceSummary("d", "D", accent = "south african"),
        ).toVoiceOptions()
        mapped.map { it.locale } shouldBe listOf(
            VoiceLocale.EN_US,
            VoiceLocale.EN_GB,
            VoiceLocale.EN_AU,
            VoiceLocale.EN_ZA,
        )
    }

    @Test
    fun `toVoiceOptions leaves unrecognised or ambiguous accents as null`() {
        // Unknown ("transatlantic"), ambiguous-within-VoiceLocale
        // ("scottish" / "irish" — would they be EN_GB? we don't claim),
        // and made-up labels all fall back to null so the locale filter
        // doesn't silently hide them.
        val mapped = listOf(
            ElevenLabsVoiceSummary("t", "T", accent = "transatlantic"),
            ElevenLabsVoiceSummary("s", "S", accent = "scottish"),
            ElevenLabsVoiceSummary("i", "I", accent = "irish"),
            ElevenLabsVoiceSummary("x", "X", accent = "klingon"),
        ).toVoiceOptions()
        mapped.forEach { it.locale shouldBe null }
    }
}
