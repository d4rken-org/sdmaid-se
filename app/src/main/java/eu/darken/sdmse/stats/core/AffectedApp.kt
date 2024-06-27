package eu.darken.sdmse.stats.core

import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.main.core.SDMTool

interface AffectedApp {
    val reportId: ReportId
    val tool: SDMTool.Type
    val action: Action
    val pkgId: Pkg.Id

    enum class Action {
        INSTALLED,
        UPDATEDD,
        DELETED,
        ;
    }
}