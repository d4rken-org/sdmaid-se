package eu.darken.sdmse.widget

import android.content.Context
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

    companion object {
        private val TAG = logTag("Widget", "Updater")
    }
}
