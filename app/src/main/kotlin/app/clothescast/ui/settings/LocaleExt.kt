package app.clothescast.ui.settings

import android.content.Context
import java.util.Locale

/** Returns the active resources locale for UI translation/sorting decisions. */
internal fun Context.resourcesLocale(): Locale {
    val locales = resources.configuration.locales
    return if (locales.isEmpty) Locale.getDefault() else locales[0]
}
