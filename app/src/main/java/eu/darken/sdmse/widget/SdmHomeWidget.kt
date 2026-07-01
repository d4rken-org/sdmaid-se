package eu.darken.sdmse.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import dagger.hilt.android.EntryPointAccessors
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.widget.ui.WidgetContent

/**
 * Home-screen widget showing per-volume storage usage + lifetime "space freed", with a Clean button.
 * State is computed on demand in [provideGlance]; refreshes are driven externally by [WidgetUpdater]
 * (post-clean / app-start / 6h backstop) and by the host (add / resize).
 *
 * Uses [SizeMode.Exact] so the composable receives the true cell size and can pick the stacked
 * (tall) vs single-row (short/wide) layout by actual height — `SizeMode.Responsive` can't express
 * that, since a large cell fits both buckets and it then picks by area.
 */
class SdmHomeWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        log(TAG, VERBOSE) { "provideGlance(id=$id)" }
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val state = entryPoint.widgetDataProvider().snapshot()
        log(TAG) { "provideGlance(id=$id): rendering $state" }

        provideContent {
            WidgetContent(state)
        }
    }

    companion object {
        private val TAG = logTag("Widget", "Home")
    }
}
