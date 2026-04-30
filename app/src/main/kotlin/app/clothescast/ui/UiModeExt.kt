package app.clothescast.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/** Returns `true` when the app is running on an Android TV / Google TV device. */
fun isTelevision(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
