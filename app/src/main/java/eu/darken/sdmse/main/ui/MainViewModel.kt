package eu.darken.sdmse.main.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.automation.core.errors.AutomationException
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.coroutine.AppCoroutineScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.NavigationDestination
import eu.darken.sdmse.common.navigation.routes.DashboardRoute
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.theming.ThemeState
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isProForUi
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.shortcuts.OneTapCleaner
import eu.darken.sdmse.main.core.themeState
import eu.darken.sdmse.main.ui.navigation.OnboardingWelcomeRoute
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.core.taskmanager.getLatestTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val oneTapCleaner: OneTapCleaner,
    private val appScope: AppCoroutineScope,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    val startRoute: NavigationDestination = if (generalSettings.isOnboardingCompleted.valueBlocking) {
        DashboardRoute
    } else {
        OnboardingWelcomeRoute
    }

    val keepScreenOn: Flow<Boolean> = taskManager.state
        .map { !it.isIdle || BuildConfigWrap.DEBUG }

    val themeState = generalSettings.themeState
        .stateIn(vmScope, SharingStarted.WhileSubscribed(5000), ThemeState())

    fun checkUpgrades() = launch {
        log(TAG) { "checkUpgrades()" }
        upgradeRepo.refresh()
    }

    private val showWidgetConsentInternal = MutableStateFlow(false)

    /** Whether the one-time "enable widget one-tap cleaning?" consent dialog should be shown. */
    val showWidgetConsent: StateFlow<Boolean> = showWidgetConsentInternal.asStateFlow()

    /** Triggered by a widget "Clean" tap when the user hasn't been asked to opt in yet. */
    fun requestWidgetConsent() {
        log(TAG, INFO) { "requestWidgetConsent()" }
        showWidgetConsentInternal.value = true
    }

    /**
     * Consent granted: remember it, enable widget one-tap, and run the scan+delete the user
     * originally intended right away. The Pro check and any upgrade navigation run on the VM scope
     * (alive with the dialog); only the actual cleaning is handed to [appScope] so it survives this
     * screen going away mid-run.
     */
    fun onWidgetConsentEnable() {
        log(TAG, INFO) { "onWidgetConsentEnable()" }
        showWidgetConsentInternal.value = false
        launch {
            generalSettings.widgetOneClickEnabled.value(true)
            generalSettings.widgetOneClickConsentAsked.value(true)
            if (!upgradeRepo.isProForUi()) {
                navTo(UpgradeRoute())
                return@launch
            }
            appScope.launch { oneTapCleaner.runOneClick(shortcutMode = true) }
        }
    }

    /**
     * Consent declined (or the dialog dismissed): remember we asked (never prompt again) and fall
     * back to a plain scan so the dashboard shows results for a normal confirmed delete.
     */
    fun onWidgetConsentDecline() {
        log(TAG, INFO) { "onWidgetConsentDecline()" }
        showWidgetConsentInternal.value = false
        appScope.launch {
            generalSettings.widgetOneClickConsentAsked.value(true)
            oneTapCleaner.runScanOnly()
        }
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

    companion object {
        private val TAG = logTag("MainActivity", "ViewModel")
    }
}
