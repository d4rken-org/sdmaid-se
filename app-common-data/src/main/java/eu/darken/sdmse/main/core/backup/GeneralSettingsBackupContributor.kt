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
        // The dismissal ordinal is anchored to THIS install's install date, which lives in the
        // curriculum_vitae store and does not travel in config backups. Restoring it onto a
        // different install would suppress that install's own anniversary. The legacy year key is
        // orphaned junk from the pre-ordinal dismissal and must not be re-imported either.
        GeneralSettings.KEY_ANNIVERSARY_DISMISSED_ORDINAL,
        "core.anniversary.dismissed.year",
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GeneralSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: GeneralSettingsBackupContributor): ConfigBackupContributor
}
