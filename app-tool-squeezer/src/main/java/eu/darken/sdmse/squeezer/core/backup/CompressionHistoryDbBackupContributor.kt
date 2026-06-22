package eu.darken.sdmse.squeezer.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.room.backup.RoomDbBackupContributor
import eu.darken.sdmse.squeezer.core.history.CompressionHistoryDatabase
import javax.inject.Inject
import javax.inject.Singleton

/** Backs up the compression history (content hashes of already-compressed media) to avoid recompression after a restore. */
@Singleton
class CompressionHistoryDbBackupContributor @Inject constructor(
    db: CompressionHistoryDatabase,
) : RoomDbBackupContributor({ db.roomDb.openHelper.writableDatabase }, TABLES) {
    override val key = "squeezer.db"

    companion object {
        private val TABLES = listOf("compression_history")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CompressionHistoryDbBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: CompressionHistoryDbBackupContributor): ConfigBackupContributor
}
