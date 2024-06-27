package eu.darken.sdmse.stats.core

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.pkgs.Pkg

interface ReportDetails {
    val status: Report.Status
        get() = Report.Status.SUCCESS

    interface AffectedSpace : ReportDetails {
        val affectedSpace: Long
    }

    interface AffectedCount : ReportDetails {
        val affectedCount: Int
    }

    interface AffectedPaths : ReportDetails, AffectedCount {
        val affectedPaths: Set<APath>

        override val affectedCount: Int
            get() = affectedPaths.size
    }

    interface AffectedPkgs : ReportDetails, AffectedCount {
        val affectedPkgs: Set<Pkg.Id>

        override val affectedCount: Int
            get() = affectedPkgs.size
    }
}