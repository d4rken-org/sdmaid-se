package eu.darken.sdmse.appcleaner.core.tasks

import eu.darken.sdmse.main.core.SDMTool

sealed interface AppCleanerTask : SDMTool.Task {
    override val type: SDMTool.Type get() = SDMTool.Type.APPCLEANER

    sealed interface Result : SDMTool.Task.Result {
        override val type: SDMTool.Type get() = SDMTool.Type.APPCLEANER
    }
}

