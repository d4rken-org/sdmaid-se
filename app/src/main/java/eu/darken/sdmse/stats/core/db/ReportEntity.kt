package eu.darken.sdmse.stats.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.core.ReportId
import java.time.Instant

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "report_id") override val reportId: ReportId,
    @ColumnInfo(name = "start_at") override val startAt: Instant,
    @ColumnInfo(name = "end_at") override val endAt: Instant,
    @ColumnInfo(name = "tool") override val tool: SDMTool.Type,
    @ColumnInfo(name = "status") override val status: Report.Status,
    @ColumnInfo(name = "error_message") override val errorMessage: String?,
) : Report {

    companion object {
        fun from(report: Report) = ReportEntity(
            reportId = report.reportId,
            startAt = report.startAt,
            endAt = report.endAt,
            tool = report.tool,
            status = report.status,
            errorMessage = report.errorMessage,
        )
    }
}