package eu.darken.sdmse.appcontrol.core.tasks

import eu.darken.sdmse.main.core.SDMTool

sealed interface AppControlTask : SDMTool.Task {
    override val type: SDMTool.Type get() = SDMTool.Type.APPCONTROL

    sealed interface Result : SDMTool.Task.Result
}

