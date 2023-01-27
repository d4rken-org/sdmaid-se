package eu.darken.sdmse.appcontrol.core

import eu.darken.sdmse.main.core.SDMTool

sealed class AppControlTask : SDMTool.Task {
    override val type: SDMTool.Type = SDMTool.Type.APPCONTROL

    sealed class Result : SDMTool.Task.Result
}

