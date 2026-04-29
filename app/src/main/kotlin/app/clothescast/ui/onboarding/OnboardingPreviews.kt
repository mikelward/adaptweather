package app.clothescast.ui.onboarding

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.clothescast.core.domain.model.Location
import app.clothescast.ui.theme.ClothesCastTheme

//
// Preview wrappers for the Onboarding screen. Same pattern as `TodayPreviews.kt` /
// `SettingsPreviews.kt`: each `@Preview internal fun` is rendered in Studio's
// design pane and in the Roborazzi snapshot test in `app/src/test`.
//
// The step cards inside `OnboardingContent` derive their "complete" checkmarks
// from runtime state read via `LocalContext` — POST_NOTIFICATIONS and
// ACCESS_COARSE_LOCATION grants. These previews only pass the *settings*-side
// flags (Gemini key, device-location toggle, picked location) directly; the
// snapshot tests grant permissions on the Robolectric application before
// rendering the variants that need a checkmark on the notification or
// location step. Studio's design pane has no permissions granted, so the
// notification + location step checkmarks and "Grant …" buttons render as if
// both perms are denied across all three previews here; the rest of each
// preview still varies with the `location` and `geminiKeyConfigured`
// parameters as you'd expect.
//

@Preview(name = "Onboarding · fresh install", widthDp = 360)
@Composable
internal fun OnboardingFreshPreview() {
    ClothesCastTheme(dynamicColor = false) {
        Surface {
            OnboardingContent(
                geminiKeyConfigured = false,
                location = null,
                useDeviceLocation = false,
                padding = PaddingValues(),
                onSetApiKey = {},
                onSetUseDeviceLocation = {},
                onSelectLocation = {},
                onSearchLocations = { emptyList() },
                onPairFromPhone = {},
                onContinue = {},
                onSkip = {},
            )
        }
    }
}

@Preview(name = "Onboarding · location picked, key pending", widthDp = 360)
@Composable
internal fun OnboardingPartialPreview() {
    ClothesCastTheme(dynamicColor = false) {
        Surface {
            OnboardingContent(
                geminiKeyConfigured = false,
                location = Location(
                    latitude = 42.36,
                    longitude = -71.06,
                    displayName = "Boston, MA",
                ),
                useDeviceLocation = false,
                padding = PaddingValues(),
                onSetApiKey = {},
                onSetUseDeviceLocation = {},
                onSelectLocation = {},
                onSearchLocations = { emptyList() },
                onPairFromPhone = {},
                onContinue = {},
                onSkip = {},
            )
        }
    }
}

@Preview(name = "Onboarding · all complete", widthDp = 360)
@Composable
internal fun OnboardingCompletePreview() {
    ClothesCastTheme(dynamicColor = false) {
        Surface {
            OnboardingContent(
                geminiKeyConfigured = true,
                location = null,
                useDeviceLocation = true,
                padding = PaddingValues(),
                onSetApiKey = {},
                onSetUseDeviceLocation = {},
                onSelectLocation = {},
                onSearchLocations = { emptyList() },
                onPairFromPhone = {},
                onContinue = {},
                onSkip = {},
            )
        }
    }
}
