package app.clothescast.update

import android.content.Context
import android.content.pm.PackageManager

/**
 * Detects whether the running APK was installed via the Play Store.
 *
 * Used to gate the in-app update banner: F-Droid / sideloaded builds can't be
 * updated through Play, so showing them an "update available" prompt would be
 * a dead end. Play's [com.google.android.play.core.appupdate.AppUpdateManager]
 * already returns `UPDATE_NOT_AVAILABLE` for non-Play installs, but checking
 * the installer up front skips the Play-Services round-trip entirely on
 * sideloads.
 *
 * The pure decision is in [isPlayStoreInstaller] so it can be unit-tested
 * without Robolectric; [isFromPlayStore] handles the Android boundary.
 */
object InstallSource {
    /**
     * Package names recognised as the Play Store installer.
     *
     * - `com.android.vending` is the modern Play Store package.
     * - `com.google.android.feedback` is the legacy installer recorded for
     *   Play installs on older system images. Devices that update to a newer
     *   Play Store keep the original installer string on records made under
     *   the older one.
     */
    private val PLAY_INSTALLERS = setOf(
        "com.android.vending",
        "com.google.android.feedback",
    )

    fun isPlayStoreInstaller(installerPackageName: String?): Boolean =
        installerPackageName in PLAY_INSTALLERS

    fun isFromPlayStore(context: Context): Boolean {
        val installer = runCatching {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        }.getOrElse { e ->
            // NameNotFoundException shouldn't happen for the running package,
            // but if it does we'd rather treat the install as non-Play
            // (suppress the banner) than crash on launch.
            if (e is PackageManager.NameNotFoundException) null else throw e
        }
        return isPlayStoreInstaller(installer)
    }
}
