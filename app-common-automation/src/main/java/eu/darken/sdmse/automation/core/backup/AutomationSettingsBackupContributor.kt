package eu.darken.sdmse.automation.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.automation.core.AutomationSettings
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.DataStoreSettingsBackupContributor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationSettingsBackupContributor @Inject constructor(
    settings: AutomationSettings,
) : DataStoreSettingsBackupContributor(settings.dataStore) {
    override val key = "automation"

    // Transient mid-operation state + device-specific probe result, not user config.
    override val excludedKeys = setOf(
        "animation.pending.restore.state",
        "acs.directwrite.unreliable.fingerprint",
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AutomationSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: AutomationSettingsBackupContributor): ConfigBackupContributor
}
