package eu.darken.sdmse.main.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.DataStoreSettingsBackupContributor
import eu.darken.sdmse.main.core.GeneralSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralSettingsBackupContributor @Inject constructor(
    settings: GeneralSettings,
) : DataStoreSettingsBackupContributor(settings.dataStore) {
    override val key = "general"

    // Derived runtime detection, not user config — recomputed per device.
    override val excludedKeys = setOf(
        "core.appops.restrictions.passed",
        "core.appops.restrictions.triggered",
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GeneralSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: GeneralSettingsBackupContributor): ConfigBackupContributor
}
