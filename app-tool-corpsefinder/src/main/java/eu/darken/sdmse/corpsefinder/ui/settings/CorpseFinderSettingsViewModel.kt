package eu.darken.sdmse.corpsefinder.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.access.AccessState
import eu.darken.sdmse.common.compose.settings.FeatureGateState
import eu.darken.sdmse.common.compose.settings.privilegedGateState
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.setup.SetupScreenOptions
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject


@HiltViewModel
class CorpseFinderSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val settings: CorpseFinderSettings,
    upgradeRepo: UpgradeRepo,
    corpseFinder: CorpseFinder,
    rootManager: RootManager,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private data class FilterToggles(
        val sdcard: Boolean,
        val publicMedia: Boolean,
        val publicData: Boolean,
        val publicObb: Boolean,
        val privateData: Boolean,
        val dalvikCache: Boolean,
        val artProfiles: Boolean,
        val appLib: Boolean,
        val appSource: Boolean,
        val appSourcePrivate: Boolean,
        val appSourceAsec: Boolean,
    )

    private val filterTogglesFlow = combine(
        settings.filterSdcardEnabled.flow,
        settings.filterPublicMediaEnabled.flow,
        settings.filterPublicDataEnabled.flow,
        settings.filterPublicObbEnabled.flow,
        settings.filterPrivateDataEnabled.flow,
        settings.filterDalvikCacheEnabled.flow,
        settings.filterArtProfilesEnabled.flow,
        settings.filterAppLibEnabled.flow,
        settings.filterAppSourceEnabled.flow,
        settings.filterAppSourcePrivateEnabled.flow,
        settings.filterAppSourceAsecEnabled.flow,
    ) { sdcard, publicMedia, publicData, publicObb, privateData, dalvik, artProfiles, appLib, appSource, appSourcePrivate, asec ->
        FilterToggles(sdcard, publicMedia, publicData, publicObb, privateData, dalvik, artProfiles, appLib, appSource, appSourcePrivate, asec)
    }

    val state: StateFlow<State> = combine(
        corpseFinder.state,
        upgradeRepo.upgradeInfo.map { it.isPro },
        settings.isWatcherEnabled.flow,
        settings.isWatcherAutoDeleteEnabled.flow,
        settings.includeRiskKeeper.flow,
        settings.includeRiskCommon.flow,
        filterTogglesFlow,
        rootManager.accessState,
    ) { cfState, isPro, watcher, autoDelete, keeper, common, filters, rootAccess ->
        State(
            isPro = isPro,
            rootAccess = rootAccess,
            rootFilterGate = privilegedGateState(cfState.isFilterPrivateDataAvailable, listOf(rootAccess)),
            isWatcherEnabled = watcher,
            isWatcherAutoDeleteEnabled = autoDelete,
            includeRiskKeeper = keeper,
            includeRiskCommon = common,
            filterSdcardEnabled = filters.sdcard,
            filterPublicMediaEnabled = filters.publicMedia,
            filterPublicDataEnabled = filters.publicData,
            filterPublicObbEnabled = filters.publicObb,
            filterPrivateDataEnabled = filters.privateData,
            filterDalvikCacheEnabled = filters.dalvikCache,
            filterArtProfilesEnabled = filters.artProfiles,
            filterAppLibEnabled = filters.appLib,
            filterAppSourceEnabled = filters.appSource,
            filterAppSourcePrivateEnabled = filters.appSourcePrivate,
            filterAppSourceAsecEnabled = filters.appSourceAsec,
            isFilterPrivateDataAvailable = cfState.isFilterPrivateDataAvailable,
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun setWatcherEnabled(value: Boolean) = launch {
        // Defence-in-depth: UI gates this row behind the upgrade badge when !isPro, but
        // refuse here too so any future caller can't bypass the check.
        if (!state.value.isPro) return@launch
        settings.isWatcherEnabled.value(value)
    }

    fun setWatcherAutoDeleteEnabled(value: Boolean) = launch {
        settings.isWatcherAutoDeleteEnabled.value(value)
    }

    fun setIncludeRiskKeeper(value: Boolean) = launch {
        settings.includeRiskKeeper.value(value)
    }

    fun setIncludeRiskCommon(value: Boolean) = launch {
        settings.includeRiskCommon.value(value)
    }

    fun setFilterSdcardEnabled(value: Boolean) = launch { settings.filterSdcardEnabled.value(value) }
    fun setFilterPublicMediaEnabled(value: Boolean) = launch { settings.filterPublicMediaEnabled.value(value) }
    fun setFilterPublicDataEnabled(value: Boolean) = launch { settings.filterPublicDataEnabled.value(value) }
    fun setFilterPublicObbEnabled(value: Boolean) = launch { settings.filterPublicObbEnabled.value(value) }
    fun setFilterPrivateDataEnabled(value: Boolean) = launch { settings.filterPrivateDataEnabled.value(value) }
    fun setFilterDalvikCacheEnabled(value: Boolean) = launch { settings.filterDalvikCacheEnabled.value(value) }
    fun setFilterArtProfilesEnabled(value: Boolean) = launch { settings.filterArtProfilesEnabled.value(value) }
    fun setFilterAppLibEnabled(value: Boolean) = launch { settings.filterAppLibEnabled.value(value) }
    fun setFilterAppSourceEnabled(value: Boolean) = launch { settings.filterAppSourceEnabled.value(value) }
    fun setFilterAppSourcePrivateEnabled(value: Boolean) = launch { settings.filterAppSourcePrivateEnabled.value(value) }
    fun setFilterAppSourceAsecEnabled(value: Boolean) = launch { settings.filterAppSourceAsecEnabled.value(value) }

    fun onWatcherBadgeClick() {
        // Non-pro: show upgrade flow without flipping the DataStore value.
        navTo(UpgradeRoute(forced = true))
    }

    fun onRootFilterBadgeClick() {
        navTo(
            SetupRoute(
                options = SetupScreenOptions(
                    showCompleted = true,
                    typeFilter = setOf(SetupModule.Type.ROOT),
                ),
            ),
        )
    }

    data class State(
        val isPro: Boolean = false,
        val rootAccess: AccessState = AccessState.Undecided,
        val rootFilterGate: FeatureGateState = FeatureGateState.SETUP,
        val isWatcherEnabled: Boolean = false,
        val isWatcherAutoDeleteEnabled: Boolean = true,
        val includeRiskKeeper: Boolean = false,
        val includeRiskCommon: Boolean = false,
        val filterSdcardEnabled: Boolean = true,
        val filterPublicMediaEnabled: Boolean = true,
        val filterPublicDataEnabled: Boolean = true,
        val filterPublicObbEnabled: Boolean = false,
        val filterPrivateDataEnabled: Boolean = true,
        val filterDalvikCacheEnabled: Boolean = false,
        val filterArtProfilesEnabled: Boolean = false,
        val filterAppLibEnabled: Boolean = false,
        val filterAppSourceEnabled: Boolean = false,
        val filterAppSourcePrivateEnabled: Boolean = false,
        val filterAppSourceAsecEnabled: Boolean = false,
        val isFilterPrivateDataAvailable: Boolean = false,
    )

    companion object {
        private val TAG = logTag("Settings", "CorpseFinder", "ViewModel")
    }
}
