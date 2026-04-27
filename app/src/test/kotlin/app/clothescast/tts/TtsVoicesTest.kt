package app.clothescast.tts

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
}
