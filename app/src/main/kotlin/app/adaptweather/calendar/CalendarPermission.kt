package app.adaptweather.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Helper for the runtime READ_CALENDAR permission. Calendar reading is opt-in: the
 * Settings toggle drives intent, this gate drives capability. Both must be true for
 * the worker to actually read events — and if the user revokes the permission from
 * system Settings, the toggle is auto-flipped off on next resume so the persisted
 * pref stays consistent with reality.
 */
object CalendarPermission {
    fun isGranted(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED

    /** The permission string callers feed into ActivityResultContracts.RequestPermission. */
    val MANIFEST_PERMISSION: String = Manifest.permission.READ_CALENDAR
}
