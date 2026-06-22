package eu.darken.sdmse.scheduler.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.scheduler.core.SchedulerSettings
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SchedulerSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val settings: SchedulerSettings,
) : ViewModel4(dispatcherProvider = dispatcherProvider, tag = TAG) {

    val state: StateFlow<State> = combine(
        settings.skipWhenPowerSaving.flow,
        settings.skipWhenNotCharging.flow,
        settings.useAutomation.flow,
    ) { skipPowerSave, skipNotCharging, useAuto ->
        State(
            skipWhenPowerSaving = skipPowerSave,
            skipWhenNotCharging = skipNotCharging,
            useAutomation = useAuto,
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun setSkipWhenPowerSaving(value: Boolean) = launch {
        settings.skipWhenPowerSaving.value(value)
    }

    fun setSkipWhenNotCharging(value: Boolean) = launch {
        settings.skipWhenNotCharging.value(value)
    }

    fun setUseAutomation(value: Boolean) = launch {
        settings.useAutomation.value(value)
    }

    data class State(
        val skipWhenPowerSaving: Boolean = true,
        val skipWhenNotCharging: Boolean = false,
        val useAutomation: Boolean = false,
    )

    companion object {
        private val TAG = logTag("Scheduler", "Settings", "ViewModel")
    }
}
