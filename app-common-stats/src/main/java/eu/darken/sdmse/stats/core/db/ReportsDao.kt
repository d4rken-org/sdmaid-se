package eu.darken.sdmse.stats.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.stats.core.ReportId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ReportsDao {

    @Query("SELECT * FROM reports WHERE report_id = :id")
    fun getById(id: ReportId): ReportEntity?

    @Query("SELECT * FROM reports")
    fun waterfall(): Flow<List<ReportEntity>>

    @Query("SELECT COUNT(*) FROM reports")
    fun reportCount(): Flow<Int>

    @Insert
    fun insert(entity: ReportEntity)

    @Query("SELECT * FROM reports WHERE end_at < :cutOff")
    fun getReportsOlderThan(cutOff: Instant): List<ReportEntity>

    @Query("DELETE FROM reports WHERE report_id IN (:ids)")
    fun delete(ids: List<ReportId>)

    @Query("SELECT * FROM reports WHERE end_at >= :since AND status IN ('SUCCESS', 'PARTIAL_SUCCESS') ORDER BY end_at ASC")
    fun getReportsSince(since: Instant): Flow<List<ReportEntity>>

    /**
     * The first report for [tool] completed at or after [since] — i.e. the deletion the dashboard's
     * freed-hero just produced (the batch's start time is passed as [since]). ASC + `id` tie-break
     * picks that report over any later background report (e.g. uninstall-watcher) for the same tool.
     * Status is intentionally NOT filtered here so the caller can branch on it (a SQL status filter
     * would silently skip a failed deletion and return an older success).
     */
    @Query("SELECT * FROM reports WHERE tool = :tool AND end_at >= :since ORDER BY end_at ASC, id ASC LIMIT 1")
    suspend fun getReportForToolSince(tool: SDMTool.Type, since: Instant): ReportEntity?
}