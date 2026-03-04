package eu.darken.sdmse.squeezer.core.tasks

import eu.darken.sdmse.main.core.SDMTool

sealed interface SqueezerTask : SDMTool.Task {
    override val type: SDMTool.Type get() = SDMTool.Type.SQUEEZER

    sealed interface Result : SDMTool.Task.Result {
        override val type: SDMTool.Type get() = SDMTool.Type.SQUEEZER
    }
}
