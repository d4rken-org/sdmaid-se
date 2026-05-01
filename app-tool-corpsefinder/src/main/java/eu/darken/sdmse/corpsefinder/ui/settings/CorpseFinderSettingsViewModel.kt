package eu.darken.sdmse.corpsefinder.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.pkgs.toggleSelfComponent
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.corpsefinder.core.watcher.UninstallWatcherReceiver
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.setup.SetupScreenOptions
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject


@HiltViewModel
class CorpseFinderSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageManager: PackageManager,
    dispatcherProvider: DispatcherProvider,
    private val settings: CorpseFinderSettings,
    upgradeRepo: UpgradeRepo,
    corpseFinder: CorpseFinder,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    init {
        // Preserve legacy receiver side-effect: when the user toggles the watcher on/off,
        // enable or disable the UninstallWatcherReceiver component. drop(1) skips the
        // initial replay so we only react to user toggles, not the first flow emission.
        settings.isWatcherEnabled.flow
            .drop(1)
            .onEach { enabled ->
                packageManager.toggleSelfComponent(
                    ComponentName(context, UninstallWatcherReceiver::class.java),
                    enabled,
                )
                log(TAG, INFO) { "New uninstall watcher state: enabled=$enabled" }
            }
            .launchInViewModel()
    }

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
    ) { cfState, isPro, watcher, autoDelete, keeper, common, filters ->
        State(
            isPro = isPro,
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
            isFilterDalvikCacheAvailable = cfState.isFilterDalvikCacheAvailable,
            isFilterArtProfilesAvailable = cfState.isFilterArtProfilesAvailable,
            isFilterAppLibrariesAvailable = cfState.isFilterAppLibrariesAvailable,
            isFilterAppSourcesAvailable = cfState.isFilterAppSourcesAvailable,
            isFilterPrivateAppSourcesAvailable = cfState.isFilterPrivateAppSourcesAvailable,
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
        val isFilterDalvikCacheAvailable: Boolean = false,
        val isFilterArtProfilesAvailable: Boolean = false,
        val isFilterAppLibrariesAvailable: Boolean = false,
        val isFilterAppSourcesAvailable: Boolean = false,
        val isFilterPrivateAppSourcesAvailable: Boolean = false,
    )

    companion object {
        private val TAG = logTag("Settings", "CorpseFinder", "ViewModel")
    }
}
