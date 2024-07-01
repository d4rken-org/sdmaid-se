package eu.darken.sdmse.stats.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import eu.darken.sdmse.stats.core.ReportId
import kotlinx.coroutines.flow.Flow

@Dao
interface AffectedFilesDao {

    @Query("SELECT * FROM affected_paths WHERE report_id = :id")
    fun getById(id: ReportId): List<AffectedPathEntity>?

    @Query("SELECT * FROM affected_paths")
    fun waterfall(): Flow<List<AffectedPathEntity>>

    @Query("SELECT COUNT(*) FROM affected_paths")
    fun filesCount(): Flow<Int>

    @Insert
    fun insert(files: List<AffectedPathEntity>)

    @Query("DELETE FROM affected_paths WHERE report_id IN (:reportIds)")
    fun delete(reportIds: List<ReportId>)
}