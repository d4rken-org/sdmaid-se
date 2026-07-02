package eu.darken.sdmse.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import dagger.hilt.android.EntryPointAccessors
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.widget.ui.WidgetContent
import eu.darken.sdmse.widget.ui.WidgetPreviewContent

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
        val provider = entryPoint.widgetDataProvider()
        val initial = provider.snapshot()
        log(TAG) { "provideGlance(id=$id): rendering $initial" }

        provideContent {
            // Collected INSIDE the composition: Glance keeps this session alive after a render, and
            // updateAll() then only recomposes this lambda without re-running provideGlance — a
            // preamble-only read would go stale (e.g. a clean started right after the last render
            // would never flip the button to working/cancel).
            val state by provider.renderState.collectAsState(initial)
            WidgetContent(state)
        }
    }

    /**
     * Generated widget-picker preview (A15+, published via [WidgetUpdater.publishPreviews]): renders
     * the real composable with representative mock data, so the picker always matches the live widget.
     * Below A15 the static `previewImage` drawable in `sdm_home_widget_info.xml` is shown instead.
     */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        log(TAG, VERBOSE) { "providePreview(widgetCategory=$widgetCategory)" }
        provideContent {
            WidgetPreviewContent(PREVIEW_STATE)
        }
    }

    companion object {
        private val TAG = logTag("Widget", "Home")

        private const val GB = 1_000_000_000L
        private val PREVIEW_STATE = WidgetRenderState.Data(
            storages = listOf(
                WidgetRenderState.Data.StorageEntry(
                    kind = WidgetRenderState.Data.StorageEntry.Kind.INTERNAL,
                    usedBytes = 45 * GB,
                    totalBytes = 128 * GB,
                ),
            ),
            freedBytes = 12 * GB,
        )
    }
}
