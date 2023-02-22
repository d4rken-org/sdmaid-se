package eu.darken.sdmse.appcleaner.core.automation

import eu.darken.sdmse.automation.core.AutomationTask
import eu.darken.sdmse.common.pkgs.Pkg

class ClearCacheTask(
    val targets: List<Pkg.Id>,
) : AutomationTask {

    data class Result(
        val successful: Collection<Pkg.Id>,
        val failed: Collection<Pkg.Id>,
    ) : AutomationTask.Result
}