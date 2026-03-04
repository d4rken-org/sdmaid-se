package eu.darken.sdmse.stats.core

import eu.darken.sdmse.common.files.APath

interface AffectedPath {
    val reportId: ReportId
    val action: Action
    val path: APath

    enum class Action {
        DELETED,
        COMPRESSED,
        ;
    }
}
