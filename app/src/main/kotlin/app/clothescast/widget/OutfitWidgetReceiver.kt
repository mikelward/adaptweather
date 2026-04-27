package app.clothescast.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * AppWidgetProvider entry point for the outfit widget. The OS routes
 * APPWIDGET_UPDATE / -ENABLED / -DISABLED broadcasts here; Glance does the
 * real work via [OutfitWidget.provideGlance].
 */
class OutfitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OutfitWidget()
}
