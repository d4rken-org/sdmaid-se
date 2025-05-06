package eu.darken.sdmse.appcleaner.core.automation

import eu.darken.sdmse.automation.core.AutomationTask
import eu.darken.sdmse.common.pkgs.features.InstallId

class ClearCacheTask(
    val targets: List<InstallId>,
    val returnToApp: Boolean,
    val onSuccess: (InstallId) -> Unit,
) : AutomationTask {

    data class Result(
        val successful: Collection<InstallId>,
        val failed: Map<InstallId, Exception>,
    ) : AutomationTask.Result
}