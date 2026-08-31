package eu.darken.sdmse.stats.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import eu.darken.sdmse.stats.core.ReportId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface AffectedPathsDao {

    @Query("SELECT * FROM affected_paths WHERE report_id = :id")
    fun getById(id: ReportId): List<AffectedPathEntity>

    @Query("SELECT * FROM affected_paths")
    fun waterfall(): Flow<List<AffectedPathEntity>>

    @Insert
    fun insert(files: List<AffectedPathEntity>)

    @Query("DELETE FROM affected_paths WHERE report_id IN (SELECT report_id FROM reports WHERE end_at < :cutOff)")
    fun deleteForReportsOlderThan(cutOff: Instant): Int

    @Query("DELETE FROM affected_paths WHERE report_id NOT IN (SELECT report_id FROM reports)")
    fun deleteOrphans(): Int
}