package eu.darken.sdmse.common.backup

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Declares the [ConfigBackupContributor] multibinding set so [ConfigBackupManager] can inject it even
 * before any contributor is bound. Actual contributors add `@Binds @IntoSet` from their own modules.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ConfigBackupModule {
    @Multibinds
    abstract fun configBackupContributors(): Set<ConfigBackupContributor>

    @Multibinds
    abstract fun databaseBackupContributors(): Set<DatabaseBackupContributor>
}
