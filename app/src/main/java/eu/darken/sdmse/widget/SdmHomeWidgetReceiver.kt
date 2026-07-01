package eu.darken.sdmse.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Manifest entry point for the home-screen widget. [GlanceAppWidgetReceiver] handles the
 * `APPWIDGET_UPDATE` / options-changed / deletion broadcasts and delegates rendering to Glance.
 */
class SdmHomeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SdmHomeWidget()
}
