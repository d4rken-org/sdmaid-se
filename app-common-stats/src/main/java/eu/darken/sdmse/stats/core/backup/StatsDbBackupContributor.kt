package eu.darken.sdmse.stats.core.backup

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.DatabaseBackupContributor
import eu.darken.sdmse.common.room.backup.RoomDbBackupContributor
import eu.darken.sdmse.stats.core.db.ReportsDatabase
import javax.inject.Inject
import javax.inject.Singleton

/** Backs up cleanup history + space snapshots (the stats database). */
@Singleton
class StatsDbBackupContributor @Inject constructor(
    @ApplicationContext context: Context,
    db: ReportsDatabase,
) : RoomDbBackupContributor(
    sqliteProvider = { db.roomDb.openHelper.writableDatabase },
    dbFileProvider = { context.getDatabasePath("reports") },
    tables = listOf("reports", "affected_paths", "affected_pkgs", "space_snapshots"),
) {
    override val key = "stats.db"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StatsDbBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: StatsDbBackupContributor): DatabaseBackupContributor
}
