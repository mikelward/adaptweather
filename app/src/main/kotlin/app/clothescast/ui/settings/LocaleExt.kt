package app.clothescast.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import java.util.Locale

/** Returns the active resources locale for UI translation/sorting decisions. */
internal fun Context.resourcesLocale(): Locale {
    val locales = resources.configuration.locales
    return if (locales.isEmpty) Locale.getDefault() else locales[0]
}

/**
 * The device's system locale, ignoring any per-app override installed by
 * [app.clothescast.locale.AppLocale.apply].
 *
 * On API 33+, [LocaleManager.getSystemLocales] is used instead of
 * [Resources.getSystem] because the OS applies per-app locale overrides at
 * the process level on those versions, which causes [Resources.getSystem] to
 * reflect the app's chosen region (e.g. de-AT) rather than the device locale.
 */
internal fun Context.deviceSystemLocale(): Locale {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val locales = getSystemService(LocaleManager::class.java)?.systemLocales
        if (locales != null && !locales.isEmpty) return locales[0]
    }
    val locales = Resources.getSystem().configuration.locales
    return if (locales.isEmpty) Locale.getDefault() else locales[0]
}
