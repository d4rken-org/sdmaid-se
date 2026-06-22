package eu.darken.sdmse.squeezer.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.DataStoreSettingsBackupContributor
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SqueezerSettingsBackupContributor @Inject constructor(
    settings: SqueezerSettings,
) : DataStoreSettingsBackupContributor(settings.dataStore) {
    override val key = "squeezer"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SqueezerSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: SqueezerSettingsBackupContributor): ConfigBackupContributor
}
