package eu.darken.sdmse.main.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel2
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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

    private val readyStateInternal = MutableStateFlow(true)
    val readyState = readyStateInternal.asLiveData2()

    val keepScreenOn = taskManager.state
        .map { !it.isIdle }
        .asLiveData2()

    fun onGo() {
        stateFlow.value = stateFlow.value.copy(ready = true)
    }

    fun checkUpgrades() = launch {
        log(TAG) { "checkUpgrades()" }
        upgradeRepo.refresh()
    }

    data class State(
        val ready: Boolean = false
    )

    companion object {
        private val TAG = logTag("MainActivity", "ViewModel")
    }
}