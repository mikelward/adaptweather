package app.clothescast.notification

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.clothescast.R

//
// Preview wrappers for the notification small icons. Android renders these as
// white silhouettes in the system status bar, so the previews tint each drawable
// white on a dark surface and show both a 48dp inspection size (so reviewers
// can see the silhouette shape) and an 18dp status-bar-realistic size next to
// it (so we can sanity-check legibility at the size users actually see).
//
// Captured to PNG by the Roborazzi snapshot test in PreviewSnapshots, so PR
// diffs surface silhouette tweaks inline without needing an emulator.
//

@Composable
private fun StatusBarSilhouette(iconRes: Int, label: String) {
    Surface(color = Color(0xFF202124)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(48.dp),
            )
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(18.dp),
            )
            Text(text = label, color = Color.White)
        }
    }
}

@Preview(name = "Notification icon · t-shirt (default)", widthDp = 360)
@Composable
internal fun NotificationIconTShirtPreview() {
    StatusBarSilhouette(R.drawable.ic_notification_insight, "t-shirt (default)")
}

@Preview(name = "Notification icon · sweater", widthDp = 360)
@Composable
internal fun NotificationIconSweaterPreview() {
    StatusBarSilhouette(R.drawable.ic_notification_top_sweater, "sweater")
}

@Preview(name = "Notification icon · thick jacket", widthDp = 360)
@Composable
internal fun NotificationIconThickJacketPreview() {
    StatusBarSilhouette(R.drawable.ic_notification_top_thick_jacket, "thick jacket")
}
