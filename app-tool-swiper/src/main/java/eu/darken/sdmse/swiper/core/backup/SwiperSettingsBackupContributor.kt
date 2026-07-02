package eu.darken.sdmse.swiper.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.DataStoreSettingsBackupContributor
import eu.darken.sdmse.swiper.core.SwiperSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwiperSettingsBackupContributor @Inject constructor(
    settings: SwiperSettings,
) : DataStoreSettingsBackupContributor(settings.dataStore) {
    override val key = "swiper"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SwiperSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: SwiperSettingsBackupContributor): ConfigBackupContributor
}
