package eu.darken.sdmse.stats.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
}