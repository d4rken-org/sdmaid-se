package eu.darken.sdmse.squeezer.core.backup

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.DatabaseBackupContributor
import eu.darken.sdmse.common.room.backup.RoomDbBackupContributor
import eu.darken.sdmse.squeezer.core.history.CompressionHistoryDatabase
import javax.inject.Inject
import javax.inject.Singleton

/** Backs up the compression history (hashes of already-compressed media) to avoid recompression after a restore. */
@Singleton
class CompressionHistoryDbBackupContributor @Inject constructor(
    @ApplicationContext context: Context,
    db: CompressionHistoryDatabase,
) : RoomDbBackupContributor(
    sqliteProvider = { db.roomDb.openHelper.writableDatabase },
    dbFileProvider = { context.getDatabasePath("compression_history") },
    tables = listOf("compression_history"),
) {
    override val key = "squeezer.db"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CompressionHistoryDbBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: CompressionHistoryDbBackupContributor): DatabaseBackupContributor
}
