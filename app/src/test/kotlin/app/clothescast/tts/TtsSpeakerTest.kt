package app.clothescast.tts

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TtsSpeakerTest {

    @Test
    fun `splits ClothesCast into two words for TTS`() {
        prepareForTts("Welcome to ClothesCast.") shouldBe "Welcome to Clothes Cast."
    }

    @Test
    fun `splits every occurrence`() {
        prepareForTts("ClothesCast preview — this is the ClothesCast voice.") shouldBe
            "Clothes Cast preview — this is the Clothes Cast voice."
    }

    @Test
    fun `leaves prose without the brand untouched`() {
        val prose = "Today will be mild. Wear a sweater. Rain at 3pm."
        prepareForTts(prose) shouldBe prose
    }
}
