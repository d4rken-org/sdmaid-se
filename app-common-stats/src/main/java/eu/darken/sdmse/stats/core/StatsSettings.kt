package eu.darken.sdmse.stats.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.serialization.json.Json
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_stats")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val retentionReports = dataStore.createValue("retention.reports", DEFAULT_RETENTION_REPORTS, json)
    val retentionPaths = dataStore.createValue("retention.paths", DEFAULT_RETENTION_PATHS, json)
    val retentionSnapshots = dataStore.createValue("retention.snapshots", DEFAULT_RETENTION_SNAPSHOTS, json)
    val lastSnapshotAt = dataStore.createValue("snapshot.last.at", 0L)

    val totalSpaceFreed = dataStore.createValue("total.space.freed", 0L)
    val totalItemsProcessed = dataStore.createValue("total.items.processed", 0L)

    companion object {
        val DEFAULT_RETENTION_REPORTS: Duration = Duration.ofDays(30)
        val DEFAULT_RETENTION_PATHS: Duration = Duration.ofDays(7)
        val DEFAULT_RETENTION_SNAPSHOTS: Duration = Duration.ofDays(90)
        val FREE_RETENTION_SNAPSHOTS: Duration = Duration.ofDays(7)
        internal val TAG = logTag("Stats", "Settings")
    }
}