package app.clothescast.ui.settings

import android.content.Context
import android.content.res.Resources
import java.util.Locale

/** Returns the active resources locale for UI translation/sorting decisions. */
internal fun Context.resourcesLocale(): Locale {
    val locales = resources.configuration.locales
    return if (locales.isEmpty) Locale.getDefault() else locales[0]
}

/**
 * The device's system locale, ignoring any per-app override installed by
 * [app.clothescast.locale.AppLocale.apply]. Read from system Resources so a
 * Region choice (e.g. de-AT) doesn't mask what the device is actually set to.
 */
internal fun deviceSystemLocale(): Locale {
    val locales = Resources.getSystem().configuration.locales
    return if (locales.isEmpty) Locale.getDefault() else locales[0]
}
