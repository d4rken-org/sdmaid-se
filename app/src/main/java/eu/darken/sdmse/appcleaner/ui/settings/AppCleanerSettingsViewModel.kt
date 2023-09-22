package eu.darken.sdmse.appcleaner.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupModule
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class AppCleanerSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val rootManager: RootManager,
    usageStatsSetupModule: UsageStatsSetupModule,
) : ViewModel3(dispatcherProvider) {


    val state = combine(
        flow { emit(rootManager.canUseRootNow()) },
        usageStatsSetupModule.state.map { it.isComplete }
    ) { canUseRoot, hasUsageStats ->
        State(
            isRooted = canUseRoot,
            hasUsageStats = hasUsageStats,
        )
    }.asLiveData2()

    data class State(
        val isRooted: Boolean,
        val hasUsageStats: Boolean,
    )

    companion object {
        private val TAG = logTag("Settings", "AppCleaner", "ViewModel")
    }
}