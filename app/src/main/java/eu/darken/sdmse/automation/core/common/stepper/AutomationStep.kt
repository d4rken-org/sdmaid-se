package eu.darken.sdmse.automation.core.common.stepper

import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString

data class AutomationStep(
    val source: String,
    val descriptionInternal: String,
    val label: CaString,
    val icon: CaDrawable? = null,
    val windowLaunch: (suspend StepContext.() -> Unit)? = null,
    val windowCheck: (suspend StepContext.() -> ACSNodeInfo)? = null,
    val nodeRecovery: (suspend StepContext.(ACSNodeInfo) -> Boolean)? = null,
    val nodeAction: (suspend StepContext.() -> Boolean)? = null,
    val timeout: Long = 15 * 1000,
) {
    override fun toString(): String = "Step(source=$source, description=$descriptionInternal)"
}