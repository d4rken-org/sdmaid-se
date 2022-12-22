package eu.darken.sdmse.corpsefinder.core.tasks

import eu.darken.sdmse.main.core.SDMTool

sealed class CorpseFinderTask : SDMTool.Task {
    override val type: SDMTool.Type = SDMTool.Type.CORPSEFINDER

    sealed class Result : SDMTool.Task.Result
}

