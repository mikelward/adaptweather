package app.adaptweather.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Helper for the runtime POST_NOTIFICATIONS permission introduced in Android 13. Versions
 * below that grant notification posting implicitly, so we report `granted = true` there.
 */
object NotificationPermission {
    fun isRequired(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun isGranted(context: Context): Boolean {
        if (!isRequired()) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** The permission string callers feed into ActivityResultContracts.RequestPermission. */
    val MANIFEST_PERMISSION: String = Manifest.permission.POST_NOTIFICATIONS
}
