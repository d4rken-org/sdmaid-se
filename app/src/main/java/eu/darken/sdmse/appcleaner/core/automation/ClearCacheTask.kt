package eu.darken.sdmse.appcleaner.core.automation

import eu.darken.sdmse.appcleaner.core.deleter.InaccessibleDeletionResult
import eu.darken.sdmse.automation.core.AutomationTask
import eu.darken.sdmse.common.pkgs.features.Installed

class ClearCacheTask(
    val targets: List<Installed.InstallId>,
) : AutomationTask {

    data class Result(
        override val successful: Collection<Installed.InstallId>,
        override val failed: Collection<Installed.InstallId>,
    ) : AutomationTask.Result, InaccessibleDeletionResult
}