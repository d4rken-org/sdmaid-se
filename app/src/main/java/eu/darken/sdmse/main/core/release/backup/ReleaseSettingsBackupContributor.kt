package eu.darken.sdmse.main.core.release.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.DataStoreSettingsBackupContributor
import eu.darken.sdmse.main.core.release.ReleaseSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReleaseSettingsBackupContributor @Inject constructor(
    settings: ReleaseSettings,
) : DataStoreSettingsBackupContributor(settings.dataStore) {
    override val key = "release"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReleaseSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: ReleaseSettingsBackupContributor): ConfigBackupContributor
}
