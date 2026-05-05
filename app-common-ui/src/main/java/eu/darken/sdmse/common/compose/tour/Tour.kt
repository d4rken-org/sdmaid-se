package eu.darken.sdmse.common.compose.tour

import eu.darken.sdmse.common.ca.CaString

@JvmInline
value class TourId(val raw: String)

data class TourStep(
    val stepId: String,
    val targetId: String = stepId,
    val title: CaString? = null,
    val body: CaString,
    val prepareTarget: (suspend () -> Unit)? = null,
)

data class TourDefinition(
    val id: TourId,
    val steps: List<TourStep>,
    val clickProtection: Boolean = true,
)

data class TourSession(
    val definition: TourDefinition,
    val stepIndex: Int,
) {
    val currentStep: TourStep get() = definition.steps[stepIndex]
    val isLast: Boolean get() = stepIndex == definition.steps.lastIndex
}
