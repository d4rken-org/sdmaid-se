package eu.darken.sdmse.common.compose.tour

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Screen-facing slice of the guided-tour API — the only operations a screen composable performs through
 * [LocalGuidedTourController]. Host/settings operations (`next`, `previous`, `dismissForever`, `disableAllTours`,
 * `markStepRendered`, `onRouteChanged`, `reset`, `complete`) stay on the concrete [GuidedTourController], which is
 * injected directly where those are needed.
 */
interface GuidedTourAccess {
    val session: StateFlow<TourSession?>
    suspend fun shouldStart(definition: TourDefinition): Boolean
    suspend fun start(definition: TourDefinition)
    suspend fun skipForNow()
}

/**
 * No-op [GuidedTourAccess] used for `@Preview` / inspection rendering, where no real controller is wired.
 * [shouldStart] returns `false` so no tour ever starts in a preview.
 */
object NoOpGuidedTourAccess : GuidedTourAccess {
    override val session: StateFlow<TourSession?> = MutableStateFlow(null)
    override suspend fun shouldStart(definition: TourDefinition): Boolean = false
    override suspend fun start(definition: TourDefinition) {}
    override suspend fun skipForNow() {}
}

/**
 * Provides the screen-facing tour API to composables. The default throws so a missing provider in the real app
 * fails loudly; previews get [NoOpGuidedTourAccess] via `PreviewWrapper` (gated on `LocalInspectionMode`).
 */
val LocalGuidedTourController = staticCompositionLocalOf<GuidedTourAccess> {
    error("GuidedTourController not provided")
}
