package eu.darken.sdmse.deduplicator.ui.settings.arbiter

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.picker.PickerRequest
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class ArbiterConfigViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val settings: DeduplicatorSettings,
) : ViewModel3(dispatcherProvider) {

    val state = settings.arbiterConfig.flow.map { config ->
        val criteriumItems = config.criteria.mapIndexed { index, criterium ->
            ArbiterConfigAdapter.CriteriumItem(
                criterium = criterium,
                position = index,
                onModeClicked = { showModeSelection(it) },
            )
        }
        State(
            items = listOf(ArbiterConfigAdapter.HeaderItem()) + criteriumItems,
        )
    }.asLiveData2()

    data class State(
        val items: List<ArbiterConfigAdapter.Item>,
    )

    fun onItemsReordered(items: List<ArbiterConfigAdapter.Item>) = launch {
        val criteriumItems = items.filterIsInstance<ArbiterConfigAdapter.CriteriumItem>()
        log(TAG) { "onItemsReordered(): ${criteriumItems.map { it.criterium::class.simpleName }}" }
        val newConfig = DeduplicatorSettings.ArbiterConfig(
            criteria = criteriumItems.map { it.criterium }
        )
        settings.arbiterConfig.value(newConfig)
    }

    private fun showModeSelection(item: ArbiterConfigAdapter.CriteriumItem) {
        log(TAG) { "showModeSelection(): ${item.criterium}" }
        when (val criterium = item.criterium) {
            is ArbiterCriterium.PreferredPath -> {
                pickerEvents.postValue(
                    PickerRequest(
                        requestKey = PICKER_REQUEST_KEY,
                        mode = PickerRequest.PickMode.DIRS,
                        allowedAreas = setOf(
                            DataArea.Type.PORTABLE,
                            DataArea.Type.SDCARD,
                            DataArea.Type.PUBLIC_DATA,
                            DataArea.Type.PUBLIC_MEDIA,
                        ),
                        selectedPaths = criterium.keepPreferPaths.toList(),
                    )
                )
            }

            else -> {
                val modes = getModeOptions(item.criterium)
                if (modes.isNotEmpty()) {
                    modeSelectionEvents.postValue(ModeSelectionEvent(item, modes))
                }
            }
        }
    }

    val modeSelectionEvents = SingleLiveEvent<ModeSelectionEvent>()
    val pickerEvents = SingleLiveEvent<PickerRequest>()

    data class ModeSelectionEvent(
        val item: ArbiterConfigAdapter.CriteriumItem,
        val modes: List<ArbiterCriterium.Mode>,
    )

    fun onModeSelected(item: ArbiterConfigAdapter.CriteriumItem, mode: ArbiterCriterium.Mode) = launch {
        log(TAG) { "onModeSelected(): ${item.criterium::class.simpleName} -> $mode" }
        val currentConfig = settings.arbiterConfig.flow.first()
        val newCriteria = currentConfig.criteria.map { criterium ->
            if (criterium::class == item.criterium::class) {
                updateCriteriumMode(criterium, mode)
            } else {
                criterium
            }
        }
        settings.arbiterConfig.value(DeduplicatorSettings.ArbiterConfig(criteria = newCriteria))
    }

    fun updatePreferredPaths(paths: Set<APath>) = launch {
        log(TAG) { "updatePreferredPaths(): $paths" }
        val currentConfig = settings.arbiterConfig.flow.first()
        val newCriteria = currentConfig.criteria.map { criterium ->
            if (criterium is ArbiterCriterium.PreferredPath) {
                criterium.copy(keepPreferPaths = paths)
            } else {
                criterium
            }
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

    companion object {
        private val TAG = logTag("Deduplicator", "Settings", "Arbiter", "ViewModel")
        const val PICKER_REQUEST_KEY = "arbiter.keep.prefer.paths"
    }
}
