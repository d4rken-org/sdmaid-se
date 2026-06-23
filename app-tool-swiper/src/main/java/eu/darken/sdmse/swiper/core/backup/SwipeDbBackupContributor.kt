package eu.darken.sdmse.swiper.core.backup

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.DatabaseBackupContributor
import eu.darken.sdmse.common.room.backup.RoomDbBackupContributor
import eu.darken.sdmse.swiper.core.db.SwiperRoomDb
import javax.inject.Inject
import javax.inject.Singleton

/** Backs up swipe sessions + their items (valid on a same-device restore; cross-device sessions point at absent paths). */
@Singleton
class SwipeDbBackupContributor @Inject constructor(
    @ApplicationContext context: Context,
    db: SwiperRoomDb,
) : RoomDbBackupContributor(
    sqliteProvider = { db.openHelper.writableDatabase },
    dbFileProvider = { context.getDatabasePath("swiper.db") },
    tables = listOf("swipe_sessions", "swipe_items"),
) {
    override val key = "swiper.db"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SwipeDbBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: SwipeDbBackupContributor): DatabaseBackupContributor
}
