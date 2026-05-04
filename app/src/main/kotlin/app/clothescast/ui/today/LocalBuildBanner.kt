package app.clothescast.ui.today

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.clothescast.BuildConfig
import kotlinx.coroutines.delay

/**
 * Dev affordance shown in place of the Play update banner on local
 * (non-CI) builds: a developer's own APK has no auto-update path, so
 * seeing the branch + commit + "2 hours ago" makes it obvious at a glance
 * which build is installed and how fresh it is.
 *
 * Gated on the same `IS_LOCAL_BUILD` flag the launcher-icon badge uses
 * (see `app/build.gradle.kts`). FAD-distributed debug APKs and Play
 * release builds (both built on CI) get neither badge nor banner —
 * `main · abc1234 · 6 days ago` would be noise to non-dev users.
 */
@Composable
internal fun LocalBuildBanner(modifier: Modifier = Modifier) {
    if (!BuildConfig.IS_LOCAL_BUILD) return
    LocalBuildBannerCard(
        branch = BuildConfig.GIT_BRANCH,
        sha = BuildConfig.GIT_SHA,
        dirty = BuildConfig.GIT_DIRTY,
        buildTimestampMs = BuildConfig.BUILD_TIMESTAMP_MS,
        modifier = modifier,
    )
}

@Composable
internal fun LocalBuildBannerCard(
    branch: String,
    sha: String,
    dirty: Boolean,
    buildTimestampMs: Long,
    modifier: Modifier = Modifier,
    nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    // Refresh "2 hours ago" once a minute so the banner stays accurate while
    // the user keeps the app open across the minute boundary.
    var now by remember { mutableLongStateOf(nowProvider()) }
    LaunchedEffect(buildTimestampMs) {
        while (true) {
            delay(60_000L)
            now = nowProvider()
        }
    }

    val relative = DateUtils.getRelativeTimeSpanString(
        buildTimestampMs,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
    val shaSuffix = if (dirty) "$sha (dirty)" else sha

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Local build",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "$branch · $shaSuffix · $relative",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
