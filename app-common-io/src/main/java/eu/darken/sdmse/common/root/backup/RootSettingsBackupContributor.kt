package eu.darken.sdmse.common.root.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.DataStoreSettingsBackupContributor
import eu.darken.sdmse.common.root.RootSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootSettingsBackupContributor @Inject constructor(
    settings: RootSettings,
) : DataStoreSettingsBackupContributor(settings.dataStore) {
    override val key = "root"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RootSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: RootSettingsBackupContributor): ConfigBackupContributor
}
