package eu.darken.sdmse.stats.core.db

import androidx.room.Dao
import androidx.room.Query
import eu.darken.sdmse.stats.core.ReportId
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportsDao {

    @Query("SELECT * FROM reports WHERE report_id = :id")
    fun getById(id: ReportId): ReportEntity?

    @Query("SELECT * FROM reports")
    fun waterfall(): Flow<List<ReportEntity>>

    // TODO fun to delete older than X days

}