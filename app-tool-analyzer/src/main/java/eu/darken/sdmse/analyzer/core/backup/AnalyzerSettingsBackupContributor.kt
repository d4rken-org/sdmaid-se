package eu.darken.sdmse.analyzer.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.analyzer.core.AnalyzerSettings
import eu.darken.sdmse.common.backup.ConfigBackupContributor
import eu.darken.sdmse.common.backup.DataStoreSettingsBackupContributor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyzerSettingsBackupContributor @Inject constructor(
    settings: AnalyzerSettings,
) : DataStoreSettingsBackupContributor(settings.dataStore) {
    override val key = "analyzer"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyzerSettingsBackupModule {
    @Binds
    @IntoSet
    abstract fun bind(c: AnalyzerSettingsBackupContributor): ConfigBackupContributor
}
