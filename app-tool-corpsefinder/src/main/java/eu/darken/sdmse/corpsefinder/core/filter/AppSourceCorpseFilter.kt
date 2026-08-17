package eu.darken.sdmse.corpsefinder.core.filter

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.corpsefinder.R
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.exclusion.core.ExclusionManager
import javax.inject.Inject
import javax.inject.Provider


@Reusable
class AppSourceCorpseFilter @Inject constructor(
    areaManager: DataAreaManager,
    gatewaySwitch: GatewaySwitch,
    fileForensics: FileForensics,
    corpseFinderSettings: CorpseFinderSettings,
    exclusionManager: ExclusionManager,
) : StandardCorpseFilter(
    filterTag = TAG,
    areaType = DataArea.Type.APP_APP,
    defaultProgressLabel = R.string.corpsefinder_filter_appsource_label,
    scanProgressLabel = R.string.corpsefinder_filter_appsource_label,
    capabilityPolicy = CapabilityPolicy.AlwaysRoot,
    // TODO needs to be checked on more rooted ROMs
    untestedFromApi = 37,
    areaManager = areaManager,
    gatewaySwitch = gatewaySwitch,
    fileForensics = fileForensics,
    corpseFinderSettings = corpseFinderSettings,
    exclusionManager = exclusionManager,
) {

    @Reusable
    class Factory @Inject constructor(
        private val settings: CorpseFinderSettings,
        private val filterProvider: Provider<AppSourceCorpseFilter>
    ) : CorpseFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterAppSourceEnabled.value()
        override suspend fun create(): CorpseFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): CorpseFilter.Factory
    }

    companion object {
        val TAG: String = logTag("CorpseFinder", "Filter", "App", "Source")
    }
}
