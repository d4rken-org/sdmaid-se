package eu.darken.sdmse.analyzer.core

import eu.darken.sdmse.main.core.SDMTool

interface AnalyzerTask : SDMTool.Task {
    override val type: SDMTool.Type get() = SDMTool.Type.ANALYZER

    interface Result : SDMTool.Task.Result {
        override val type: SDMTool.Type get() = SDMTool.Type.ANALYZER
    }
}

