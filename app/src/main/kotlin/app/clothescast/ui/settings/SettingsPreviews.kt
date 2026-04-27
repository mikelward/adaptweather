package app.clothescast.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.clothescast.ui.theme.ClothesCastTheme

//
// Preview wrappers for the Settings screens. Same pattern as `TodayPreviews.kt`:
// each `@Preview internal fun` is rendered both in Studio's design pane and in
// the Roborazzi snapshot test in `app/src/test`.
//
// The Settings root is the cheapest one to capture and the one most prone to
// silent reorderings (the row order is driven by `SettingsRoute.entries`,
// which is one-line in `SettingsScreen.kt`). A snapshot here means any future
// reorder shows up as a pixel diff in the PR's "Files changed" view.
//

@Preview(name = "Settings · root list", widthDp = 360)
@Composable
internal fun SettingsRootPreview() {
    ClothesCastTheme(dynamicColor = false) {
        Surface { SettingsRoot(padding = PaddingValues(0.dp), onNavigate = {}) }
    }
}
