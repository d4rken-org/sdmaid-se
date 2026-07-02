package eu.darken.sdmse.appcleaner.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.DataStoreSettingsBackupContributor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCleanerSettingsBackupContributor @Inject constructor(
    settings: AppCleanerSettings,
) : DataStoreSettingsBackupContributor(settings.dataStore) {
    override val key = "appcleaner"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppCleanerSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: AppCleanerSettingsBackupContributor): ConfigBackupContributor
}
