package app.clothescast.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.clothescast.R

//
// Preview wrapper for the adaptive launcher icon. Captured to PNG by the
// Roborazzi snapshot test in PreviewSnapshots so PR diffs surface any change
// to the foreground t-shirt or the background colour inline — no emulator,
// no Play Store screenshot round-trip.
//
// Compose's painterResource doesn't render <adaptive-icon> XML directly
// (it's not a vector or bitmap), so we reconstruct the adaptive composition
// from its two layers — background drawable + foreground mipmap — both
// drawn at the same size so the launcher's safe-zone proportions are
// preserved. The three side-by-side renders mimic the three mask shapes
// real launchers apply (square / circle / Pixel squircle), so a glance at
// the snapshot answers "does the t-shirt fit inside the circular safe zone".
//

@Composable
private fun MaskedLauncherIcon(shape: Shape, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(shape)
            .background(colorResource(id = R.color.ic_launcher_background)),
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
        )
    }
}

@Preview(name = "Launcher icon · masks", widthDp = 360)
@Composable
internal fun LauncherIconPreview() {
    Surface(color = Color(0xFFEFEFEF)) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaskedLauncherIcon(shape = RectangleShape)
            MaskedLauncherIcon(shape = CircleShape)
            MaskedLauncherIcon(shape = RoundedCornerShape(20.dp))
        }
    }
}
