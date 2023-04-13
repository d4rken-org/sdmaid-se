package eu.darken.sdmse.appcleaner.core.automation

import eu.darken.sdmse.automation.core.AutomationTask
import eu.darken.sdmse.common.pkgs.UserPkgId

class ClearCacheTask(
    val targets: List<UserPkgId>,
) : AutomationTask {

    data class Result(
        val successful: Collection<UserPkgId>,
        val failed: Collection<UserPkgId>,
    ) : AutomationTask.Result
}