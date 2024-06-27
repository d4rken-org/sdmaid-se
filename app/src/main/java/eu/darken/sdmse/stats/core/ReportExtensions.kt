package eu.darken.sdmse.stats.core

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.pkgs.Pkg

val ReportDetails.affectedCount: Int?
    get() = (this as? ReportDetails.AffectedCount)?.affectedCount

val ReportDetails.affectedSpace: Long?
    get() = (this as? ReportDetails.AffectedSpace)?.affectedSpace

val ReportDetails.affectedPaths: Collection<APath>?
    get() = (this as? ReportDetails.AffectedPaths)?.affectedPaths

val ReportDetails.affectedPkgs: Collection<Pkg.Id>?
    get() = (this as? ReportDetails.AffectedPkgs)?.affectedPkgs