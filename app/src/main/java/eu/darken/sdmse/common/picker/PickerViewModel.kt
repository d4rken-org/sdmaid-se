package eu.darken.sdmse.common.picker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.isDescendantOf
import eu.darken.sdmse.common.files.isDirectory
import eu.darken.sdmse.common.files.lookup
import eu.darken.sdmse.common.files.lookupFiles
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.first
import okio.IOException
import javax.inject.Inject

@HiltViewModel
class PickerViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    dataAreaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<PickerFragmentArgs>()
    private val request = navArgs.request

    private val selectedItems = MutableStateFlow(emptyList<APathLookup<*>>())
    private val navigationState = MutableStateFlow<List<PickerItem>?>(null)
    val events = SingleLiveEvent<PickerEvent>()

    init {
        log(TAG, INFO) { "Request: $request" }
        launch {
            log(TAG) { "Reserving resources..." }
            gatewaySwitch.sharedResource.get().use { _ ->
                log(TAG) { "Resources reserved!" }
                awaitCancellation()
            }
        }
        launch {
            log(TAG) { "Loading pre-selected paths: ${request.selectedPaths}" }
            request.selectedPaths
                .mapNotNull {
                    try {
                        it.lookup(gatewaySwitch)
                    } catch (e: IOException) {
                        log(TAG) { "lookup for preselected path failed: $it\n${e.asLog()}" }
                        errorEvents.postValue(e)
                        null
                    }
                }
                .let { selectedItems.value = it }
        }
    }

    private val internalState = combineTransform(
        dataAreaManager.state,
        navigationState,
        selectedItems,
    ) { areaState, navState, selected ->
        emit(State())

        val current: PickerItem? = navState?.lastOrNull()
        val items = when {
            current == null ->
                areaState.areas
                    .sortedBy { it.type }
                    .filter { request.allowedAreas.isEmpty() || request.allowedAreas.contains(it.type) }
                    .map { area ->
                        PickerItem(
                            dataArea = area,
                            lookup = area.path.lookup(gatewaySwitch),
                            parent = null,
                            selected = selected.any { it.lookedUp == area.path },
                            selectable = true,
                        )
                    }
                    .map {
                        PickerItemVH.Item(
                            item = it,
                            onItemClicked = {
                                navigationState.value = (navigationState.value ?: emptyList()) + it
                            },
                            onSelect = { select(setOf(it.lookup)) },
                        )
                    }

            else -> navState.last()
                .lookup.lookupFiles(gatewaySwitch)
                .sortedWith(compareByDescending<APathLookup<*>> { it.isDirectory }.thenBy { it.name })
                .filter {
                    when (request.mode) {
                        PickerRequest.PickMode.DIR,
                        PickerRequest.PickMode.DIRS -> it.isDirectory
                    }
                }
                .map { lookup ->
                    PickerItem(
                        dataArea = areaState.areas
                            .sortedByDescending { it.path.segments.size }
                            .first { it.path.isAncestorOf(lookup.lookedUp) },
                        lookup = lookup,
                        parent = current,
                        selected = selected.any { it.lookedUp == lookup.lookedUp },
                        selectable = true,
                    )
                }
                .map {
                    PickerItemVH.Item(
                        item = it,
                        onItemClicked = {
                            navigationState.value = (navigationState.value ?: emptyList()) + it
                        },
                        onSelect = { select(setOf(it.lookup)) }
                    )
                }
        }

        val selectedItems = selected
            .reversed()
            .map {
                PickerSelectedVH.Item(
                    lookup = it,
                    onRemove = { select(setOf(it)) }
                )
            }

        State(
            current = current,
            items = items,
            selected = selectedItems,
            hasChanges = selected.map { it.lookedUp } != request.selectedPaths,
            progress = null,
        ).run { emit(this) }
    }.replayingShare(viewModelScope)

    val state = internalState.asLiveData2()

    fun selectAll() = launch {
        log(TAG) { "selectAll()" }
        val toSelect = internalState.first().items
            .filterIsInstance<PickerItemVH.Item>()
            .map { it.item.lookup }
            .sortedByDescending { it.lookedUp.segments.size }
        select(toSelect)
    }

    fun home() {
        log(TAG) { "home()" }
        navigationState.value = null
    }

    fun cancel(confirmed: Boolean) {
        log(TAG) { "cancel(confirmed=$confirmed)" }
        if (!confirmed && state.value?.hasChanges == true) {
            events.postValue(PickerEvent.ExitConfirmation)
            return
        }
        popNavStack()
    }

    fun goBack() {
        log(TAG) { "goBack()" }
        navigationState.value?.let { cur ->
            navigationState.value = cur.dropLast(1).takeIf { it.isNotEmpty() }
        } ?: run { cancel(confirmed = false) }
    }

    fun select(paths: Collection<APathLookup<*>>) {
        log(TAG) { "select($paths)" }
        val newSelectedItems = selectedItems.value.toMutableList()
        paths.forEach { path ->
            val removed = newSelectedItems.removeIf { it.lookedUp == path.lookedUp }
            if (!removed) {
                newSelectedItems.removeIf { path.isAncestorOf(it) }
                newSelectedItems.removeIf { path.isDescendantOf(it) }
                newSelectedItems.add(path)
            }
        }
        selectedItems.value = newSelectedItems
    }

    fun save() {
        log(TAG) { "save()" }
        val result = PickerResult(
            selectedPaths = selectedItems.value.map { it.lookedUp }.toSet(),
        )
        events.postValue(PickerEvent.Save(requestKey = request.requestKey, result = result))
    }

    data class State(
        val current: PickerItem? = null,
        val items: List<PickerAdapter.Item> = emptyList(),
        val selected: List<PickerSelectedAdapter.Item> = emptyList(),
        val hasChanges: Boolean = false,
        val progress: Progress.Data? = Progress.Data(),
    )

    companion object {
        private val TAG = logTag("Common", "Picker", "ViewModel")
    }
}