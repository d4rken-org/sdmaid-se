package eu.darken.sdmse.stats.core

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.main.core.SDMTool

interface AffectedPath {
    val reportId: ReportId
    val tool: SDMTool.Type
    val action: Action
    val path: APath

    enum class Action {
        DELETED,
        ;
    }
}