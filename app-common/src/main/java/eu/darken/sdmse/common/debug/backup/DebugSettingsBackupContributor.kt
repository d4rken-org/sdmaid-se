package eu.darken.sdmse.common.debug.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.DataStoreSettingsBackupContributor
import eu.darken.sdmse.common.debug.DebugSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugSettingsBackupContributor @Inject constructor(
    settings: DebugSettings,
) : DataStoreSettingsBackupContributor(settings.dataStore) {
    override val key = "debug"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: DebugSettingsBackupContributor): ConfigBackupContributor
}
