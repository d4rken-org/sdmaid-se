package eu.darken.sdmse.stats.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.DataStoreSettingsBackupContributor
import eu.darken.sdmse.stats.core.StatsSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsSettingsBackupContributor @Inject constructor(
    settings: StatsSettings,
) : DataStoreSettingsBackupContributor(settings.dataStore) {
    override val key = "stats"

    // Internal snapshot bookkeeping; the lifetime totals (highscore) are intentionally kept.
    override val excludedKeys = setOf(
        "snapshot.last.at",
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StatsSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: StatsSettingsBackupContributor): ConfigBackupContributor
}
