package eu.darken.sdmse.compressor.core.tasks

import eu.darken.sdmse.main.core.SDMTool

sealed interface CompressorTask : SDMTool.Task {
    override val type: SDMTool.Type get() = SDMTool.Type.COMPRESSOR

    sealed interface Result : SDMTool.Task.Result {
        override val type: SDMTool.Type get() = SDMTool.Type.COMPRESSOR
    }
}
