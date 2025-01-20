package eu.darken.sdmse.main.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.automation.core.errors.AutomationException
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel2
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.core.taskmanager.getLatestTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.Duration
import java.time.Instant
import javax.inject.Inject


@HiltViewModel
class MainViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @Suppress("unused") private val handle: SavedStateHandle,
    private val upgradeRepo: UpgradeRepo,
    private val taskManager: TaskManager,
) : ViewModel2(dispatcherProvider = dispatcherProvider) {

    private val stateFlow = MutableStateFlow(State())
    val state = stateFlow
        .onEach { log(VERBOSE) { "New state: $it" } }
        .asLiveData2()

    val errorEvents = SingleLiveEvent<Throwable>()

    private val readyStateInternal = MutableStateFlow(true)
    val readyState = readyStateInternal.asLiveData2()

    val keepScreenOn = taskManager.state
        .map { !it.isIdle || BuildConfigWrap.DEBUG }
        .asLiveData2()

    fun onGo() {
        stateFlow.value = stateFlow.value.copy(ready = true)
    }

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
                errorEvents.postValue(error)
            }
    }

    data class State(
        val ready: Boolean = false
    )

    companion object {
        private val TAG = logTag("MainActivity", "ViewModel")
    }
}