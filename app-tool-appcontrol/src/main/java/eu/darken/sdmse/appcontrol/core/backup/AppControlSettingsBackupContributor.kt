package eu.darken.sdmse.appcontrol.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcontrol.core.AppControlSettings
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.DataStoreSettingsBackupContributor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppControlSettingsBackupContributor @Inject constructor(
    settings: AppControlSettings,
) : DataStoreSettingsBackupContributor(settings.dataStore) {
    override val key = "appcontrol"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppControlSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: AppControlSettingsBackupContributor): ConfigBackupContributor
}
