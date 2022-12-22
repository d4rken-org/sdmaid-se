package eu.darken.sdmse.appcleaner.core.tasks

import eu.darken.sdmse.main.core.SDMTool

sealed class AppCleanerTask : SDMTool.Task {
    override val type: SDMTool.Type = SDMTool.Type.APPCLEANER

    sealed class Result : SDMTool.Task.Result
}

