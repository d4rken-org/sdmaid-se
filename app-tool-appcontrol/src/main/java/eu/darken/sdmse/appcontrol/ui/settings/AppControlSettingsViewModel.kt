package eu.darken.sdmse.appcontrol.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppControlSettings
import eu.darken.sdmse.appcontrol.core.FilterSettings
import eu.darken.sdmse.appcontrol.core.SortSettings
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.setup.SetupScreenOptions
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject


@HiltViewModel
class AppControlSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    appControl: AppControl,
    upgradeRepo: UpgradeRepo,
    private val settings: AppControlSettings,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    init {
        settings.moduleSizingEnabled.flow
            .onEach { enabled ->
                if (!enabled && settings.listSort.value().mode == SortSettings.Mode.SIZE) {
                    settings.listSort.value(SortSettings())
                }
            }
            .launchInViewModel()
        settings.moduleActivityEnabled.flow
            .onEach { enabled ->
                val curFilter = settings.listFilter.value()
                if (!enabled && curFilter.tags.contains(FilterSettings.Tag.ACTIVE)) {
                    settings.listFilter.value(
                        curFilter.copy(tags = curFilter.tags - FilterSettings.Tag.ACTIVE)
                    )
                }
            }
            .launchInViewModel()
    }

    val state: StateFlow<State> = combine(
        appControl.state,
        upgradeRepo.upgradeInfo.map { it.isPro },
        settings.moduleSizingEnabled.flow,
        settings.moduleActivityEnabled.flow,
        settings.includeMultiUserEnabled.flow,
    ) { appState, isPro, sizingEnabled, activityEnabled, multiUserEnabled ->
        State(
            isPro = isPro,
            sizingEnabled = sizingEnabled,
            activityEnabled = activityEnabled,
            multiUserEnabled = multiUserEnabled,
            canInfoSize = appState.canInfoSize,
            canInfoActive = appState.canInfoActive,
            canIncludeMultiUser = appState.canIncludeMultiUser,
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun setSizingEnabled(value: Boolean) = launch {
        settings.moduleSizingEnabled.value(value)
    }

    fun setActivityEnabled(value: Boolean) = launch {
        settings.moduleActivityEnabled.value(value)
    }

    fun setMultiUserEnabled(value: Boolean) = launch {
        // Defence-in-depth: UI gates this row behind the upgrade badge when !isPro, but
        // refuse here too so any future caller can't bypass the check.
        if (!state.value.isPro) return@launch
        settings.includeMultiUserEnabled.value(value)
    }

    fun onSizingBadgeClick() {
        navTo(SetupRoute(options = SetupScreenOptions(showCompleted = true, typeFilter = setOf(SetupModule.Type.USAGE_STATS))))
    }

    fun onActivityBadgeClick() {
        navTo(SetupRoute(options = SetupScreenOptions(showCompleted = true, typeFilter = setOf(SetupModule.Type.USAGE_STATS))))
    }

    fun onMultiUserBadgeClick() {
        val s = state.value
        if (!s.isPro) {
            navTo(UpgradeRoute(forced = true))
        } else {
            navTo(
                SetupRoute(
                    options = SetupScreenOptions(
                        showCompleted = true,
                        typeFilter = setOf(SetupModule.Type.ROOT, SetupModule.Type.SHIZUKU),
                    ),
                ),
            )
        }
    }

    data class State(
        val isPro: Boolean = false,
        val sizingEnabled: Boolean = true,
        val activityEnabled: Boolean = true,
        val multiUserEnabled: Boolean = false,
        val canInfoSize: Boolean = false,
        val canInfoActive: Boolean = false,
        val canIncludeMultiUser: Boolean = false,
    )

    companion object {
        private val TAG = logTag("Settings", "AppControl", "ViewModel")
    }
}
