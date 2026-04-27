package app.clothescast.core.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GarmentTest {

    @Test
    fun `default rule items round-trip through the catalog`() {
        // The defaults shipped in ClothesRule.DEFAULTS must all be picker-editable;
        // if a default's item key isn't a Garment, the editor would have no way
        // to round-trip it through the dropdown.
        ClothesRule.DEFAULTS.forEach { rule ->
            val garment = Garment.fromKey(rule.item)
            (garment != null) shouldBe true
            garment!!.itemKey shouldBe rule.item
        }
    }

    @Test
    fun `fromKey is case-insensitive and trims whitespace`() {
        Garment.fromKey("Sweater") shouldBe Garment.SWEATER
        Garment.fromKey("  shorts  ") shouldBe Garment.SHORTS
    }

    @Test
    fun `fromKey accepts common spelling variants`() {
        // Pre-catalog rules may have used these variants when the editor was
        // free-form — round-trip them so the dropdown can preselect correctly.
        Garment.fromKey("tshirt") shouldBe Garment.TSHIRT
        Garment.fromKey("trousers") shouldBe Garment.PANTS
        Garment.fromKey("long pants") shouldBe Garment.PANTS
        Garment.fromKey("jumper") shouldBe Garment.SWEATER
    }

    @Test
    fun `fromKey returns null for unknown items`() {
        Garment.fromKey("kilt") shouldBe null
        Garment.fromKey("") shouldBe null
    }
}
