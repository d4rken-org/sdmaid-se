package eu.darken.sdmse.systemcleaner.core.tasks

import eu.darken.sdmse.main.core.SDMTool

sealed class SystemCleanerTask : SDMTool.Task {
    override val type: SDMTool.Type = SDMTool.Type.SYSTEMCLEANER

    sealed class Result : SDMTool.Task.Result
}

