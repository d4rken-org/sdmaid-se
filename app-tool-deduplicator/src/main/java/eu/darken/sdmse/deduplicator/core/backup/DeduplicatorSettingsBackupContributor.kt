package eu.darken.sdmse.deduplicator.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.DataStoreSettingsBackupContributor
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeduplicatorSettingsBackupContributor @Inject constructor(
    settings: DeduplicatorSettings,
) : DataStoreSettingsBackupContributor(settings.dataStore) {
    override val key = "deduplicator"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DeduplicatorSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: DeduplicatorSettingsBackupContributor): ConfigBackupContributor
}
