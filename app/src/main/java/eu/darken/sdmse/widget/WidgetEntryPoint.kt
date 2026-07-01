package eu.darken.sdmse.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bridges Hilt into [SdmHomeWidget.provideGlance], where field injection is unavailable.
 * Resolve via `EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetDataProvider(): WidgetDataProvider
}
