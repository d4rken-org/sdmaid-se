package eu.darken.sdmse.widget

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Triggers a re-render of all live [SdmHomeWidget] instances. Suspends until Glance has finished, so
 * callers (e.g. a worker) can await it. No-ops cheaply when no widget is placed.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun updateAll() {
        try {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(SdmHomeWidget::class.java)
            if (ids.isEmpty()) {
                log(TAG, VERBOSE) { "updateAll(): no widget instances placed, skipping" }
                return
            }
            log(TAG, VERBOSE) { "updateAll(): refreshing ${ids.size} widget(s)" }
            SdmHomeWidget().updateAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "updateAll() failed: ${e.asLog()}" }
        }
    }

    /**
     * Publish the Glance-generated widget-picker preview ([SdmHomeWidget.providePreview], A15+).
     * The system rate-limits this (~2/hour, result [GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_RATE_LIMITED]),
     * so it's called once per process start (app launch), NOT from [updateAll].
     */
    suspend fun publishPreviews() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        try {
            val result = GlanceAppWidgetManager(context).setWidgetPreviews(SdmHomeWidgetReceiver::class)
            log(TAG) { "publishPreviews(): result=$result" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "publishPreviews() failed: ${e.asLog()}" }
        }
    }

    companion object {
        private val TAG = logTag("Widget", "Updater")
    }
}
