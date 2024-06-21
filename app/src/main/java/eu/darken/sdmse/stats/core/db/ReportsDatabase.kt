package eu.darken.sdmse.stats.core.db

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.core.ReportId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportsDatabase @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val database by lazy {
        Room.databaseBuilder(context, ReportsRoomDb::class.java, "reports").build()
    }

    suspend fun find(id: ReportId): Report? = database.reports().getById(id)
        .also { log(TAG, VERBOSE) { "find($id) -> it" } }


    companion object {
        internal val TAG = logTag("Stats", "Reports", "Database")
    }
}