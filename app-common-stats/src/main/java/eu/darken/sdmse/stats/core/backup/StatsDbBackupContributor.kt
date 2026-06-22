package eu.darken.sdmse.stats.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.room.backup.RoomDbBackupContributor
import eu.darken.sdmse.stats.core.db.ReportsDatabase
import javax.inject.Inject
import javax.inject.Singleton

/** Backs up cleanup history + space snapshots (the stats database). */
@Singleton
class StatsDbBackupContributor @Inject constructor(
    db: ReportsDatabase,
) : RoomDbBackupContributor(db.roomDb, TABLES) {
    override val key = "stats.db"

    companion object {
        // Parents first.
        private val TABLES = listOf("reports", "affected_paths", "affected_pkgs", "space_snapshots")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StatsDbBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: StatsDbBackupContributor): ConfigBackupContributor
}
