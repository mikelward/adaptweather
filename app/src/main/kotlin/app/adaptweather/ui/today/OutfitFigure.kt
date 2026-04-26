package app.adaptweather.ui.today

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.adaptweather.R
import app.adaptweather.core.domain.model.OutfitSuggestion

/**
 * The drawable layers that make up the cartoon outfit figure on the Today
 * screen, plus a per-appearance set of tint colours to render them with.
 *
 * Designed as a small data class so the call site (currently a fixed
 * defaults set, eventually user preferences) can build one and pass it in,
 * without the figure renderer having to know where the colours came from.
 *
 * TODO(customization): once SettingsRepository surfaces user-configured
 * garment colours and skin tone, the TodayViewModel should resolve those
 * into an [OutfitAppearance] and expose it as part of [TodayState], replacing
 * the [defaultsFor] call inside the figure renderer.
 *
 * TODO(customization): when we add a gender-variant base figure, extend this
 * data class with a `bodyShape` enum and pick the corresponding skin/features
 * drawables in [resolve]. The garment overlays should keep working as-is
 * because they're authored in the same shared coordinate system.
 */
@Immutable
data class OutfitAppearance(
    val skinTone: Color,
    val topColor: Color,
    val bottomColor: Color,
) {
    companion object {
        // Neutral peach — reads as "person" rather than "silhouette" while staying
        // unspecific. User-selectable skin tone is the eventual replacement.
        val NEUTRAL_SKIN_TONE: Color = Color(0xFFE8C2A0)

        fun defaultsFor(outfit: OutfitSuggestion): OutfitAppearance = OutfitAppearance(
            skinTone = NEUTRAL_SKIN_TONE,
            topColor = defaultTopColor(outfit.top),
            bottomColor = defaultBottomColor(outfit.bottom),
        )

        private fun defaultTopColor(top: OutfitSuggestion.Top): Color = when (top) {
            OutfitSuggestion.Top.TSHIRT -> Color(0xFF4FC3F7)        // sky blue
            OutfitSuggestion.Top.SWEATER -> Color(0xFFFF8A65)       // warm coral
            OutfitSuggestion.Top.THICK_JACKET -> Color(0xFF1E88E5)  // deep blue
        }

        private fun defaultBottomColor(bottom: OutfitSuggestion.Bottom): Color = when (bottom) {
            OutfitSuggestion.Bottom.SHORTS -> Color(0xFFD7B17E)     // tan / khaki
            OutfitSuggestion.Bottom.LONG_PANTS -> Color(0xFF5C6BC0) // indigo
        }
    }
}

/**
 * Renders the cartoon outfit figure: an androgynous character wearing the
 * top + bottom from [outfit], coloured per [appearance].
 *
 * Implementation is a [Box] of stacked [Image]s using shared 96 × 96 drawable
 * coordinates so they line up: skin → bottom garment → top garment → face.
 * Each clothing/skin layer is rendered with [ColorFilter.tint] so the same
 * silhouette can recolour without authoring multiple drawables. The face
 * (eyes, smile) is intentionally NOT tinted — it stays black regardless of
 * skin/garment colour.
 *
 * TODO(customization): see [OutfitAppearance] for the customization plumbing
 * roadmap (user-selected colours, skin tone, gender variants, garment styles).
 * TODO(notification): the daily-insight notification is a great surface for
 * this same figure as the large icon. Render the composable to a [Bitmap]
 * via Compose's [androidx.compose.ui.graphics.painter.Painter]→Canvas path
 * and pass to NotificationCompat.Builder.setLargeIcon — see [InsightNotifier].
 */
@Composable
internal fun OutfitFigure(
    outfit: OutfitSuggestion,
    modifier: Modifier = Modifier,
    appearance: OutfitAppearance = OutfitAppearance.defaultsFor(outfit),
) {
    Box(modifier = modifier) {
        FigureLayer(R.drawable.ic_person_skin, tint = appearance.skinTone)
        FigureLayer(bottomGarmentDrawable(outfit.bottom), tint = appearance.bottomColor)
        FigureLayer(topGarmentDrawable(outfit.top), tint = appearance.topColor)
        // Features render last so eyes/smile sit on top of every garment.
        // No tint — features are fixed black so the face is always readable.
        FigureLayer(R.drawable.ic_person_features, tint = null)
    }
}

@Composable
private fun FigureLayer(@DrawableRes resId: Int, tint: Color?) {
    // fillMaxSize (not matchParentSize) because this composable isn't a
    // BoxScope-receiver function — it's called from any Composable context.
    // The Box at the call site has a definite size from its own modifier,
    // so fillMaxSize gives the same visual result as matchParentSize would.
    Image(
        painter = painterResource(id = resId),
        contentDescription = null, // Composed parent supplies a single description.
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}

@DrawableRes
private fun topGarmentDrawable(top: OutfitSuggestion.Top): Int = when (top) {
    OutfitSuggestion.Top.TSHIRT -> R.drawable.ic_garment_top_tshirt
    OutfitSuggestion.Top.SWEATER -> R.drawable.ic_garment_top_sweater
    OutfitSuggestion.Top.THICK_JACKET -> R.drawable.ic_garment_top_thick_jacket
}

@DrawableRes
private fun bottomGarmentDrawable(bottom: OutfitSuggestion.Bottom): Int = when (bottom) {
    OutfitSuggestion.Bottom.SHORTS -> R.drawable.ic_garment_bottom_shorts
    OutfitSuggestion.Bottom.LONG_PANTS -> R.drawable.ic_garment_bottom_long_pants
}
