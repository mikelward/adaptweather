package app.adaptweather.ui.today

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

//
// Calls every preview wrapper in `TodayPreviews.kt` and captures the result to
// PNG under `app/src/test/snapshots/roborazzi/` — a *tracked* path, so GitHub
// renders image diffs natively in the PR's "Files changed" view. CI commits
// any new/updated PNGs back to the PR branch (see ci.yml), so reviewers see
// pixel changes inline without downloading anything. `roborazzi.test.record=true`
// in the build script means each run re-records; the diff "gate" is just
// "the PNGs in the repo changed". When/if a hard-fail regression gate is
// wanted on stable surfaces, switch those tests to verifyRoborazzi*.
//
// **Sizing:** Studio honours each `@Preview`'s `widthDp` / `heightDp` in its
// design pane, but those parameters are *metadata* — calling the preview
// composable directly (as we do here) doesn't apply them. Snapshots render
// at the Robolectric device size from `@Config(qualifiers = ...)` below.
// We pin `widthDp = 360` on each `@Preview` to match the qualifier so Studio
// and snapshots agree on width; vertical extent is whatever the content
// needs at that width.
//
// To add a new captured state, add a `@Preview internal fun XxxPreview()`
// in `TodayPreviews.kt` and a one-line `capture { XxxPreview() }` test below.
//
// Robolectric `@GraphicsMode(NATIVE)` switches to the real Skia pipeline so
// Compose can actually rasterise. SDK is pinned so renders are reproducible
// across host machines.
//
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h640dp-xhdpi")
class PreviewSnapshots {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun capture(content: @Composable () -> Unit) {
        composeRule.setContent { content() }
        composeRule.onRoot().captureRoboImage()
    }

    @Test fun today_empty_state() = capture { EmptyStatePreview() }
    @Test fun today_insight_card() = capture { InsightCardPreview() }
    @Test fun today_insight_card_dark() = capture { InsightCardDarkPreview() }

    @Test fun confidence_high() = capture { ConfidenceHighPreview() }
    @Test fun confidence_medium() = capture { ConfidenceMediumPreview() }
    @Test fun confidence_low() = capture { ConfidenceLowPreview() }

    @Test fun work_status_running() = capture { WorkStatusRunningPreview() }
    @Test fun work_status_failed() = capture { WorkStatusFailedPreview() }
}
