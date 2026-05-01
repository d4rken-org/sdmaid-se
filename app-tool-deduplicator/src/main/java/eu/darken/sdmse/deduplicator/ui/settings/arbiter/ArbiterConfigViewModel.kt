package eu.darken.sdmse.deduplicator.ui.settings.arbiter

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.NavigationController
import eu.darken.sdmse.common.picker.PickerRequest
import eu.darken.sdmse.common.picker.PickerResultKey
import eu.darken.sdmse.common.picker.PickerRoute
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class ArbiterConfigViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val settings: DeduplicatorSettings,
    navCtrl: NavigationController,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    val state: StateFlow<State> = settings.arbiterConfig.flow
        .map { State(criteria = it.criteria) }
        .safeStateIn(
            initialValue = State(),
            onError = { State() },
        )

    val events = SingleEventFlow<Event>()

    init {
        navCtrl.consumeResults(PickerResultKey(PICKER_REQUEST_KEY))
            .onEach { result ->
                log(TAG) { "Picker returned ${result.selectedPaths.size} preferred paths" }
                updatePreferredPaths(result.selectedPaths)
            }
            .launchIn(vmScope)
    }

    fun onCriteriumClick(criterium: ArbiterCriterium) {
        when (criterium) {
            is ArbiterCriterium.PreferredPath -> navTo(
                PickerRoute(
                    request = PickerRequest(
                        requestKey = PICKER_REQUEST_KEY,
                        mode = PickerRequest.PickMode.DIRS,
                        allowedAreas = setOf(
                            DataArea.Type.PORTABLE,
                            DataArea.Type.SDCARD,
                            DataArea.Type.PUBLIC_DATA,
                            DataArea.Type.PUBLIC_MEDIA,
                        ),
                        selectedPaths = criterium.keepPreferPaths.toList(),
                    ),
                ),
            )

            else -> {
                val modes = getModeOptions(criterium)
                if (modes.isNotEmpty()) {
                    events.tryEmit(Event.ShowModeSelection(criterium, modes))
                }
            }
        }
    }

    fun onItemsReordered(newOrder: List<ArbiterCriterium>) = launch {
        log(TAG) { "onItemsReordered(): ${newOrder.map { it::class.simpleName }}" }
        settings.arbiterConfig.value(DeduplicatorSettings.ArbiterConfig(criteria = newOrder))
    }

    fun onModeSelected(criterium: ArbiterCriterium, mode: ArbiterCriterium.Mode) = launch {
        log(TAG) { "onModeSelected(): ${criterium::class.simpleName} -> $mode" }
        val current = settings.arbiterConfig.flow.first()
        val newCriteria = current.criteria.map {
            if (it::class == criterium::class) updateCriteriumMode(it, mode) else it
        }
        settings.arbiterConfig.value(DeduplicatorSettings.ArbiterConfig(criteria = newCriteria))
    }

    private fun updatePreferredPaths(paths: Set<eu.darken.sdmse.common.files.APath>) = launch {
        val current = settings.arbiterConfig.flow.first()
        val newCriteria = current.criteria.map {
            if (it is ArbiterCriterium.PreferredPath) it.copy(keepPreferPaths = paths) else it
        }
        settings.arbiterConfig.value(DeduplicatorSettings.ArbiterConfig(criteria = newCriteria))
    }

    fun resetToDefaults() = launch {
        log(TAG) { "resetToDefaults()" }
        settings.arbiterConfig.value(DeduplicatorSettings.ArbiterConfig())
    }

    private fun getModeOptions(criterium: ArbiterCriterium): List<ArbiterCriterium.Mode> = when (criterium) {
        is ArbiterCriterium.DuplicateType -> ArbiterCriterium.DuplicateType.Mode.entries
        is ArbiterCriterium.MediaProvider -> ArbiterCriterium.MediaProvider.Mode.entries
        is ArbiterCriterium.Location -> ArbiterCriterium.Location.Mode.entries
        is ArbiterCriterium.Nesting -> ArbiterCriterium.Nesting.Mode.entries
        is ArbiterCriterium.Modified -> ArbiterCriterium.Modified.Mode.entries
        is ArbiterCriterium.Size -> ArbiterCriterium.Size.Mode.entries
        is ArbiterCriterium.PreferredPath -> emptyList()
    }

    private fun updateCriteriumMode(criterium: ArbiterCriterium, mode: ArbiterCriterium.Mode): ArbiterCriterium =
        when (criterium) {
            is ArbiterCriterium.DuplicateType -> criterium.copy(mode = mode as ArbiterCriterium.DuplicateType.Mode)
            is ArbiterCriterium.MediaProvider -> criterium.copy(mode = mode as ArbiterCriterium.MediaProvider.Mode)
            is ArbiterCriterium.Location -> criterium.copy(mode = mode as ArbiterCriterium.Location.Mode)
            is ArbiterCriterium.Nesting -> criterium.copy(mode = mode as ArbiterCriterium.Nesting.Mode)
            is ArbiterCriterium.Modified -> criterium.copy(mode = mode as ArbiterCriterium.Modified.Mode)
            is ArbiterCriterium.Size -> criterium.copy(mode = mode as ArbiterCriterium.Size.Mode)
            is ArbiterCriterium.PreferredPath -> criterium
        }

    data class State(val criteria: List<ArbiterCriterium> = emptyList())

    sealed interface Event {
        data class ShowModeSelection(
            val criterium: ArbiterCriterium,
            val modes: List<ArbiterCriterium.Mode>,
        ) : Event
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Settings", "Arbiter", "ViewModel")
        private const val PICKER_REQUEST_KEY = "arbiter.keep.prefer.paths"
    }
}
