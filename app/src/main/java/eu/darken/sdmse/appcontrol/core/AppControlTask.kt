package eu.darken.sdmse.appcontrol.core

import eu.darken.sdmse.main.core.SDMTool

interface AppControlTask : SDMTool.Task {
    override val type: SDMTool.Type get() = SDMTool.Type.APPCONTROL

    interface Result : SDMTool.Task.Result {
        override val type: SDMTool.Type get() = SDMTool.Type.APPCONTROL
    }
}

