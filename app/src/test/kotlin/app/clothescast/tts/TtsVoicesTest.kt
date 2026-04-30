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
    private val japanese = TtsVoiceOption("j", "Junko — JP", VoiceLocale.JA_JP)

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
    fun `falls back to same-language voices when no exact accent matches`() {
        // No EN_AU voice in the list, but both en-US and en-GB share the
        // "en" language subtag with en-AU. Better to surface those near-
        // matches than dump every voice — and certainly better than leaving
        // the picker empty.
        val all = listOf(american, british, japanese)
        all.filterByVariant(VoiceLocale.EN_AU)
            .shouldContainExactlyInAnyOrder(american, british)
    }

    @Test
    fun `accent-agnostic voices ride along with the language-fallback tier`() {
        // Mixed list (e.g. refreshed ElevenLabs library where one voice's
        // accent label didn't parse → locale=null). en-AU user: the unknown-
        // accent voice rides along with the same-language matches rather
        // than being the *only* voice shown. Without this, a single
        // unparseable voice would hide the en-* near-matches and leave the
        // user with one weirdly-named voice and no caption.
        val all = listOf(american, british, japanese, agnostic)
        all.filterByVariant(VoiceLocale.EN_AU)
            .shouldContainExactlyInAnyOrder(american, british, agnostic)
    }

    @Test
    fun `accent-agnostic voices ride along with the full-list fallback tier`() {
        // No "en" voice and a single null-locale voice. The agnostic voice
        // still appears (it's part of `this` when we return the full list);
        // the test mostly locks in that nothing magic happens here.
        val all = listOf(japanese, agnostic)
        all.filterByVariant(VoiceLocale.EN_AU) shouldBe all
    }

    @Test
    fun `falls back to the full list when nothing in the source speaks that language`() {
        // No "en" voice at all — showing the Japanese voice is the least bad
        // option. The picker UI surfaces a "try another accent" caption in
        // this case (see localeFallbackTier).
        val all = listOf(japanese)
        all.filterByVariant(VoiceLocale.EN_AU) shouldBe all
    }

    @Test
    fun `localeFallbackTier reports Exact when at least one voice matches`() {
        val all = listOf(american, british)
        all.localeFallbackTier(VoiceLocale.EN_GB) shouldBe LocaleFallbackTier.Exact
    }

    @Test
    fun `localeFallbackTier reports SameLanguage on language-only fallback`() {
        val all = listOf(american, british)
        all.localeFallbackTier(VoiceLocale.EN_AU) shouldBe LocaleFallbackTier.SameLanguage
    }

    @Test
    fun `localeFallbackTier reports FullList when no language match exists`() {
        val all = listOf(japanese)
        all.localeFallbackTier(VoiceLocale.EN_AU) shouldBe LocaleFallbackTier.FullList
    }

    @Test
    fun `localeFallbackTier reports Exact for SYSTEM regardless of source`() {
        // SYSTEM disables filtering, so there's no fallback caption to show.
        val all = listOf(japanese)
        all.localeFallbackTier(VoiceLocale.SYSTEM) shouldBe LocaleFallbackTier.Exact
    }

    @Test
    fun `localeFallbackTier treats an all-agnostic source as Exact`() {
        // Gemini's prebuilt voices are language-agnostic — there's no
        // accent claim to fall back *from*, so the picker doesn't caption.
        val all = listOf(agnostic)
        all.localeFallbackTier(VoiceLocale.JA_JP) shouldBe LocaleFallbackTier.Exact
    }

    @Test
    fun `localeFallbackTier ignores agnostic voices when other voices have known accents`() {
        // Mixed list where the *known-accent* voices don't include en-AU:
        // tier should be SameLanguage so the picker captions the fallback.
        // Previously a single null-locale voice would have collapsed the
        // detection to Exact and hidden the caption.
        val all = listOf(american, british, japanese, agnostic)
        all.localeFallbackTier(VoiceLocale.EN_AU) shouldBe LocaleFallbackTier.SameLanguage
    }

    @Test
    fun `localeFallbackTier reports FullList on a mixed source with no language match`() {
        // Same shape as above but the user's language isn't represented
        // among the known-accent voices either. Caption should be the
        // "try another accent" copy.
        val all = listOf(japanese, agnostic)
        all.localeFallbackTier(VoiceLocale.EN_AU) shouldBe LocaleFallbackTier.FullList
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
        // No "en" voice at all → tier=FullList, returns the source as-is
        // (which already includes selected). keepSelected becomes a no-op
        // rather than re-appending.
        val all = listOf(japanese)
        all.filterByVariant(VoiceLocale.EN_AU, keepSelected = "j") shouldBe all
    }

    @Test
    fun `keepSelected appends a different-language voice on language-tier fallback`() {
        // en-AU user with `j` (Japanese) persisted: tier resolves to
        // SameLanguage, returning the en-* voices. keepSelected ensures the
        // user's current pick still shows in the dialog so the button
        // label doesn't fall back to a raw voice id.
        val all = listOf(american, british, japanese)
        all.filterByVariant(VoiceLocale.EN_AU, keepSelected = "j")
            .shouldContainExactlyInAnyOrder(american, british, japanese)
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
