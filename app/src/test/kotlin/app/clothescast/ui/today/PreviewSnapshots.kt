package app.clothescast.ui.today

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.clothescast.notification.NotificationIconSweaterPreview
import app.clothescast.notification.NotificationIconTShirtPreview
import app.clothescast.notification.NotificationIconThickJacketPreview
import app.clothescast.ui.settings.SettingsRootPreview
import app.clothescast.widget.WidgetEmptyPreview
import app.clothescast.widget.WidgetTodayJacketPantsPreview
import app.clothescast.widget.WidgetTodayTShirtShortsPreview
import app.clothescast.widget.WidgetTonightDarkPreview
import app.clothescast.widget.WidgetTonightSweaterPantsPreview
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

//
// Calls every preview wrapper across the app — `ui/today/TodayPreviews.kt`,
// `notification/NotificationIconPreviews.kt`, `ui/settings/SettingsPreviews.kt`,
// `widget/WidgetPreviews.kt` — and captures each to PNG under
// `app/snapshots/` — a *tracked* path, so GitHub renders image diffs natively
// in the PR's "Files changed" view. CI commits any new/updated PNGs back to
// the PR branch (see ci.yml), so reviewers see pixel changes inline without
// downloading anything. `roborazzi.test.record=true` in the build script means
// each run re-records; the diff "gate" is just "the PNGs in the repo changed".
// When/if a hard-fail regression gate is wanted on stable surfaces, switch
// those tests to verifyRoborazzi*.
//
// **Sizing:** Studio honours each `@Preview`'s `widthDp` / `heightDp` in its
// design pane, but those parameters are *metadata* — calling the preview
// composable directly (as we do here) doesn't apply them. Snapshots render
// at the Robolectric device size from `@Config(qualifiers = ...)` below.
// We pin `widthDp = 360` on each `@Preview` to match the qualifier so Studio
// and snapshots agree on width; vertical extent is whatever the content
// needs at that width.
//
// To add a new captured state, add a `@Preview internal fun XxxPreview()` to
// the relevant `*Previews.kt` file and a one-line `capture { XxxPreview() }`
// test below.
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

    // Override Roborazzi's default `<package>.<class>.<method>.png` naming with
    // just `<method>.png`. The PNGs all live under `app/snapshots/` already, so
    // the package + class prefix was pure noise on every filename and every
    // `git mv` if the test class ever got renamed or moved.
    @get:Rule
    val testName = TestName()

    // `captureRoboImage(filePath = ...)` resolves a bare filename against the test
    // working dir (the `:app` module root), bypassing `roborazzi.output.dir` from
    // the build script. Prefix it explicitly so PNGs land under the tracked
    // snapshots directory and CI's `git add app/snapshots` finds them.
    private val outputDir: String = System.getProperty("roborazzi.output.dir")
        ?: error("roborazzi.output.dir not set; configure in app/build.gradle.kts testOptions")

    private fun capture(content: @Composable () -> Unit) {
        composeRule.setContent { content() }
        composeRule.onRoot().captureRoboImage(filePath = "$outputDir/${testName.methodName}.png")
    }

    @Test fun outfit_tshirt_shorts() = capture { OutfitTShirtShortsPreview() }
    @Test fun outfit_tshirt_pants() = capture { OutfitTShirtPantsPreview() }
    @Test fun outfit_sweater_shorts() = capture { OutfitSweaterShortsPreview() }
    @Test fun outfit_sweater_pants() = capture { OutfitSweaterPantsPreview() }
    @Test fun outfit_jacket_shorts() = capture { OutfitJacketShortsPreview() }
    @Test fun outfit_jacket_pants() = capture { OutfitJacketPantsPreview() }
    @Test fun outfit_sweater_pants_dark() = capture { OutfitSweaterPantsDarkPreview() }
    @Test fun outfit_row_today_tonight() = capture { OutfitRowTodayTonightPreview() }
    @Test fun outfit_row_tonight_tomorrow() = capture { OutfitRowTonightTomorrowPreview() }

    @Test fun today_empty_state() = capture { EmptyStatePreview() }
    @Test fun today_insight_card() = capture { InsightCardPreview() }
    @Test fun today_insight_card_dark() = capture { InsightCardDarkPreview() }

    @Test fun confidence_high() = capture { ConfidenceHighPreview() }
    @Test fun confidence_medium() = capture { ConfidenceMediumPreview() }
    @Test fun confidence_low() = capture { ConfidenceLowPreview() }

    @Test fun work_status_running() = capture { WorkStatusRunningPreview() }
    @Test fun work_status_failed() = capture { WorkStatusFailedPreview() }

    @Test fun notification_icon_tshirt() = capture { NotificationIconTShirtPreview() }
    @Test fun notification_icon_sweater() = capture { NotificationIconSweaterPreview() }
    @Test fun notification_icon_thick_jacket() = capture { NotificationIconThickJacketPreview() }

    @Test fun widget_today_tshirt_shorts() = capture { WidgetTodayTShirtShortsPreview() }
    @Test fun widget_tonight_sweater_pants() = capture { WidgetTonightSweaterPantsPreview() }
    @Test fun widget_today_jacket_pants() = capture { WidgetTodayJacketPantsPreview() }
    @Test fun widget_tonight_dark() = capture { WidgetTonightDarkPreview() }
    @Test fun widget_empty() = capture { WidgetEmptyPreview() }

    @Test fun settings_root() = capture { SettingsRootPreview() }
}
