package eu.darken.sdmse.common.compose.tour

import eu.darken.sdmse.common.ca.CaString

@JvmInline
value class TourId(val raw: String)

/**
 * A single tour step.
 *
 * [targetId]: the [Modifier.guidedTourTarget] id this step anchors to. `null` means the step is
 * centerless — no cutout, no tail, bubble centered on screen. Anchored steps that can't find their
 * target within the host's grace window are auto-skipped; centerless steps never auto-skip because
 * they don't need a target.
 */
data class TourStep(
    val stepId: String,
    val targetId: String? = stepId,
    val title: CaString? = null,
    val body: CaString,
    val prepareTarget: (suspend () -> Unit)? = null,
)

data class TourDefinition(
    val id: TourId,
    val steps: List<TourStep>,
    val clickProtection: Boolean = true,
    /**
     * Runs after a successful completion has been persisted and the active session has been
     * cleared. It is not invoked for skip, dismiss, disable-all, or a no-render grace-skip.
     */
    val onComplete: (suspend () -> Unit)? = null,
)

data class TourSession(
    val definition: TourDefinition,
    val stepIndex: Int,
) {
    val currentStep: TourStep get() = definition.steps[stepIndex]
    val isLast: Boolean get() = stepIndex == definition.steps.lastIndex
}
