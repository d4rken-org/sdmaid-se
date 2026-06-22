package eu.darken.sdmse.scheduler.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.DataStoreSettingsBackupContributor
import eu.darken.sdmse.scheduler.core.SchedulerSettings
import javax.inject.Inject
import javax.inject.Singleton

/** Scheduler *settings*; the schedule entries themselves are a separate content contributor. */
@Singleton
class SchedulerSettingsBackupContributor @Inject constructor(
    settings: SchedulerSettings,
) : DataStoreSettingsBackupContributor(settings.dataStore) {
    override val key = "scheduler.settings"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SchedulerSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: SchedulerSettingsBackupContributor): ConfigBackupContributor
}
