package eu.darken.sdmse.systemcleaner.core.tasks

import eu.darken.sdmse.main.core.SDMTool

sealed interface SystemCleanerTask : SDMTool.Task {
    override val type: SDMTool.Type get() = SDMTool.Type.SYSTEMCLEANER


    sealed interface Result : SDMTool.Task.Result {
        override val type: SDMTool.Type get() = SDMTool.Type.SYSTEMCLEANER
    }
}

