package eu.darken.sdmse.stats.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import eu.darken.sdmse.stats.core.ReportId
import kotlinx.coroutines.flow.Flow

@Dao
interface AffectedPkgsDao {

    @Query("SELECT * FROM affected_pkgs WHERE report_id = :id")
    fun getById(id: ReportId): List<AffectedPkgEntity>

    @Query("SELECT * FROM affected_pkgs")
    fun waterfall(): Flow<List<AffectedPkgEntity>>

    @Query("SELECT COUNT(*) FROM affected_pkgs")
    fun pkgscount(): Flow<Int>

    @Insert
    fun insert(files: List<AffectedPkgEntity>)

    @Query("DELETE FROM affected_pkgs WHERE report_id IN (:reportIds)")
    fun delete(reportIds: List<ReportId>)
}