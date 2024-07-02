package eu.darken.sdmse.stats.core

import eu.darken.sdmse.common.pkgs.Pkg

interface AffectedApp {
    val reportId: ReportId
    val action: Action
    val pkgId: Pkg.Id

    enum class Action {
        INSTALLED,
        UPDATEDD,
        DELETED,
        ;
    }
}