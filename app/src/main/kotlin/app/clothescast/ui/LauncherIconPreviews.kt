package app.clothescast.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.clothescast.R

// Renders the two-layer adaptive icon composition (background + foreground)
// clipped to a circle to approximate how the launcher displays the icon.
// Captured by PreviewSnapshots so icon changes are visible in PR diffs.

@Composable
private fun AdaptiveIconFrame(foreground: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(24.dp)) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            foreground()
        }
    }
}

@Preview(name = "Launcher icon · release", widthDp = 120)
@Composable
internal fun LauncherIconPreview() {
    AdaptiveIconFrame {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
