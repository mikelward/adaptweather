package app.clothescast.ui.settings

import android.content.Context
import java.util.Locale

internal fun Context.resourcesLocale(): Locale = resources.configuration.locales[0]
