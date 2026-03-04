package eu.darken.sdmse.stats.core

import eu.darken.sdmse.common.pkgs.Pkg

interface AffectedPkg {
    val reportId: ReportId
    val action: Action
    val pkgId: Pkg.Id

    enum class Action {
        EXPORTED,
        STOPPED,
        ENABLED,
        DISABLED,
        DELETED,
        ARCHIVED,
        RESTORED,
        ;
    }
}
