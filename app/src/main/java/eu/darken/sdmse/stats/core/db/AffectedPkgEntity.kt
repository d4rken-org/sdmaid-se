package eu.darken.sdmse.stats.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.stats.core.AffectedPkg
import eu.darken.sdmse.stats.core.ReportId

@Entity(tableName = "affected_pkgs")
data class AffectedPkgEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "report_id") override val reportId: ReportId,
    @ColumnInfo(name = "action") override val action: AffectedPkg.Action,
    @ColumnInfo(name = "pkg_id") override val pkgId: Pkg.Id,
) : AffectedPkg {
    companion object {
        fun from(report: AffectedPkg) = AffectedPkgEntity(
            reportId = report.reportId,
            action = report.action,
            pkgId = report.pkgId,
        )
    }
}