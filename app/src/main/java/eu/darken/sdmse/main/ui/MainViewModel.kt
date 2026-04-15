package eu.darken.sdmse.main.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.automation.core.errors.AutomationException
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.NavigationDestination
import eu.darken.sdmse.common.navigation.routes.DashboardRoute
import eu.darken.sdmse.common.theming.ThemeState
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.ui.navigation.OnboardingWelcomeRoute
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.core.taskmanager.getLatestTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @Suppress("unused") private val handle: SavedStateHandle,
    private val upgradeRepo: UpgradeRepo,
    private val taskManager: TaskManager,
    private val generalSettings: GeneralSettings,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    val startRoute: NavigationDestination = if (generalSettings.isOnboardingCompleted.valueBlocking) {
        DashboardRoute
    } else {
        OnboardingWelcomeRoute
    }

    val state: Flow<State> = MutableStateFlow(State())

    val keepScreenOn: Flow<Boolean> = taskManager.state
        .map { !it.isIdle || BuildConfigWrap.DEBUG }

    val themeState = combine(
        generalSettings.themeMode.flow,
        generalSettings.themeStyle.flow,
    ) { mode, style ->
        ThemeState(mode = mode, style = style)
    }.stateIn(vmScope, SharingStarted.WhileSubscribed(5000), ThemeState())

    fun checkUpgrades() = launch {
        log(TAG) { "checkUpgrades()" }
        upgradeRepo.refresh()
    }

    private var handledErrors: Set<String>
        get() = handle["handledErrors"] ?: emptySet()
        set(value) {
            handle["handledErrors"] = value
        }

    fun checkErrors() = launch {
        log(TAG) { "checkErrors()" }
        val state = taskManager.state.first()

        state.getLatestTask(SDMTool.Type.APPCLEANER)
            ?.takeIf { !handledErrors.contains(it.id) }
            ?.takeIf { Duration.between(it.completedAt!!, Instant.now()) < Duration.ofSeconds(10) }
            ?.let { task ->
                val error = task.error as? AutomationException ?: return@let
                handledErrors = handledErrors + task.id
                errorEvents.tryEmit(error)
            }
    }

    data class State(
        val ready: Boolean = false,
    )

    companion object {
        private val TAG = logTag("MainActivity", "ViewModel")
    }
}
