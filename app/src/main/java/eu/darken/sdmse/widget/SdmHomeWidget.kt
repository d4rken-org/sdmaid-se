package eu.darken.sdmse.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import dagger.hilt.android.EntryPointAccessors
import eu.darken.sdmse.widget.ui.WidgetContent

/**
 * Home-screen widget showing primary-storage usage + lifetime "space freed", with a one-tap clean
 * button. State is computed on demand in [provideGlance]; refreshes are driven externally by
 * [WidgetUpdater] (post-clean / app-start / 6h backstop) and by the host (add / resize).
 *
 * Uses [SizeMode.Single] with a single vertical layout that fills the cell. The minimum size in the
 * `appwidget-provider` XML guarantees the layout always has room to render — a responsive split is
 * unnecessary for this content and was prone to picking an overflowing layout at small sizes.
 */
class SdmHomeWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val state = entryPoint.widgetDataProvider().snapshot()

        provideContent {
            WidgetContent(state)
        }
    }
}
