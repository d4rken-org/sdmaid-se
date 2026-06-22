package eu.darken.sdmse.common.adb.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.adb.AdbSettings
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.DataStoreSettingsBackupContributor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdbSettingsBackupContributor @Inject constructor(
    settings: AdbSettings,
) : DataStoreSettingsBackupContributor(settings.dataStore) {
    override val key = "adb"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AdbSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: AdbSettingsBackupContributor): ConfigBackupContributor
}
