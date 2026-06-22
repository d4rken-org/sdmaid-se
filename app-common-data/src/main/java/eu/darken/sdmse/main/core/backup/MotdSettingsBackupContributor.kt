package eu.darken.sdmse.main.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.DataStoreSettingsBackupContributor
import eu.darken.sdmse.main.core.motd.MotdSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MotdSettingsBackupContributor @Inject constructor(
    settings: MotdSettings,
) : DataStoreSettingsBackupContributor(settings.dataStore) {
    override val key = "motd"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MotdSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: MotdSettingsBackupContributor): ConfigBackupContributor
}
