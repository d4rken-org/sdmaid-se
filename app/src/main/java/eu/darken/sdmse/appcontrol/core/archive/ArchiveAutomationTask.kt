package eu.darken.sdmse.appcontrol.core.archive

import eu.darken.sdmse.automation.core.AutomationTask
import eu.darken.sdmse.common.pkgs.features.InstallId

class ArchiveAutomationTask(
    val targets: List<InstallId>,
) : AutomationTask {

    data class Result(
        val successful: Collection<InstallId>,
        val failed: Collection<InstallId>,
    ) : AutomationTask.Result
}
