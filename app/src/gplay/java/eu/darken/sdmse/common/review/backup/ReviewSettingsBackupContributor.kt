package eu.darken.sdmse.common.review.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.DataStoreSettingsBackupContributor
import eu.darken.sdmse.common.review.ReviewSettings
import javax.inject.Inject
import javax.inject.Singleton

/** GPLAY-only. On a FOSS restore this section has no matching contributor and is skipped. */
@Singleton
class ReviewSettingsBackupContributor @Inject constructor(
    settings: ReviewSettings,
) : DataStoreSettingsBackupContributor(settings.dataStore) {
    override val key = "review.gplay"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: ReviewSettingsBackupContributor): ConfigBackupContributor
}
