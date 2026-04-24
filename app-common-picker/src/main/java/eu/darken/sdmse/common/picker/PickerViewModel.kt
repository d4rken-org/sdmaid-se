package eu.darken.sdmse.common.picker

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.isDescendantOf
import eu.darken.sdmse.common.files.isDirectory
import eu.darken.sdmse.common.files.lookup
import eu.darken.sdmse.common.files.lookupFiles
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.navigation.NavigationController
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel4
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import okio.IOException
import javax.inject.Inject

@HiltViewModel
class PickerViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val dataAreaManager: DataAreaManager,
    private val navCtrl: NavigationController,
    private val gatewaySwitch: GatewaySwitch,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    /** Set once via [setRequest] — Navigation3 passes the route through the entry composable. */
    private val requestFlow = MutableStateFlow<PickerRequest?>(null)
    private val request: PickerRequest
        get() = requestFlow.value ?: error("PickerRequest not set yet")

    /** In-memory, looked-up view of selected paths; source of truth for state composition. */
    private val selectedItems = MutableStateFlow(emptyList<APathLookup<*>>())

    /** In-memory nav breadcrumb of [PickerItem]s from data-area root → current folder. */
    private val navigationState = MutableStateFlow<List<PickerItem>?>(null)

    val events = SingleEventFlow<Event>()

    init {
        launch {
            log(TAG) { "Reserving resources..." }
            gatewaySwitch.sharedResource.get().use { _ ->
                log(TAG) { "Resources reserved!" }
                awaitCancellation()
            }
        }
    }

    /**
     * Called once from the Host when Navigation3 hands us the destination. Triggers hydration
     * from either the [SavedStateHandle] (process-death restore) or the [PickerRequest]'s
     * pre-selected paths.
     */
    fun setRequest(newRequest: PickerRequest) {
        val existing = requestFlow.value
        if (existing == newRequest) return
        if (existing != null) {
            log(TAG, INFO) { "Request already set — ignoring second call" }
            return
        }
        log(TAG, INFO) { "setRequest($newRequest)" }
        requestFlow.value = newRequest
        launch {
            val restoredSelected = handle.get<ArrayList<APath>>(KEY_SELECTED)
            val restoredNav = handle.get<ArrayList<APath>>(KEY_NAV_PATH)
            if (restoredSelected != null || restoredNav != null) {
                log(TAG, INFO) { "Restoring from SavedStateHandle (selected=${restoredSelected?.size}, nav=${restoredNav?.size})" }
                hydrateFromSavedState(
                    savedSelected = restoredSelected ?: arrayListOf(),
                    savedNav = restoredNav ?: arrayListOf(),
                )
            } else {
                hydrateFromRequest()
            }
        }
    }

    private suspend fun hydrateFromRequest() {
        log(TAG) { "Loading pre-selected paths: ${request.selectedPaths}" }
        val preSelected = request.selectedPaths.mapNotNull { it.safeLookup() }
        selectedItems.value = preSelected
        persistSelected(preSelected)

        val firstSelected = preSelected.firstOrNull() ?: return
        val areaState = dataAreaManager.state.first()
        val targetArea = areaState.areas
            .filter { request.allowedAreas.isEmpty() || request.allowedAreas.contains(it.type) }
            .sortedByDescending { it.path.segments.size }
            .firstOrNull { it.path.isAncestorOf(firstSelected.lookedUp) || it.path == firstSelected.lookedUp }
            ?: return

        val navPath = mutableListOf<PickerItem>()
        val targetSegments = firstSelected.lookedUp.segments
        val areaSegments = targetArea.path.segments.size

        val areaLookup = targetArea.path.lookup(gatewaySwitch)
        navPath.add(
            PickerItem(
                dataArea = targetArea,
                lookup = areaLookup,
                parent = null,
                selected = false,
                selectable = true,
            ),
        )

        for (i in areaSegments until targetSegments.size) {
            val childSegments = targetSegments.subList(areaSegments, i + 1).toTypedArray()
            val childPath = targetArea.path.child(*childSegments)
            val lookup = try {
                childPath.lookup(gatewaySwitch)
            } catch (e: IOException) {
                log(TAG) { "Failed to lookup nav path segment: $childPath" }
                break
            }
            navPath.add(
                PickerItem(
                    dataArea = targetArea,
                    lookup = lookup,
                    parent = navPath.lastOrNull(),
                    selected = false,
                    selectable = true,
                ),
            )
        }
        // Drop the leaf so the selected path itself stays visible in the list.
        if (navPath.size > 1) navPath.removeAt(navPath.lastIndex)

        log(TAG, INFO) { "Pre-navigating to: ${navPath.map { it.lookup.lookedUp }}" }
        val navToSet = navPath.takeIf { it.isNotEmpty() }
        navigationState.value = navToSet
        persistNav(navToSet)
    }

    private suspend fun hydrateFromSavedState(
        savedSelected: ArrayList<APath>,
        savedNav: ArrayList<APath>,
    ) {
        val rehydratedSelected = savedSelected.mapNotNull { it.safeLookup() }
        selectedItems.value = rehydratedSelected

        if (savedNav.isEmpty()) {
            navigationState.value = null
            return
        }
        val areaState = dataAreaManager.state.first()
        val rehydratedNav = mutableListOf<PickerItem>()
        for (path in savedNav) {
            val area = areaState.areas
                .sortedByDescending { it.path.segments.size }
                .firstOrNull { it.path.isAncestorOf(path) || it.path == path }
                ?: break
            val lookup = path.safeLookup() ?: break
            rehydratedNav.add(
                PickerItem(
                    dataArea = area,
                    lookup = lookup,
                    parent = rehydratedNav.lastOrNull(),
                    selected = false,
                    selectable = true,
                ),
            )
        }
        navigationState.value = rehydratedNav.takeIf { it.isNotEmpty() }
    }

    private suspend fun APath.safeLookup(): APathLookup<*>? = try {
        lookup(gatewaySwitch)
    } catch (e: IOException) {
        log(TAG) { "lookup for path failed: $this\n${e.asLog()}" }
        errorEvents.emit(e)
        null
    }

    private fun persistSelected(items: List<APathLookup<*>>) {
        handle[KEY_SELECTED] = ArrayList(items.map { it.lookedUp })
    }

    private fun persistNav(nav: List<PickerItem>?) {
        handle[KEY_NAV_PATH] = nav?.mapTo(ArrayList()) { it.lookup.lookedUp } ?: arrayListOf()
    }

    private val internalState = combineTransform(
        requestFlow.filterNotNull(),
        dataAreaManager.state,
        navigationState,
        selectedItems,
    ) { req, areaState, navState, selected ->
        emit(State())

        val current: PickerItem? = navState?.lastOrNull()
        val items: List<PickerRow> = when {
            current == null -> areaState.areas
                .sortedBy { it.type }
                .filter { req.allowedAreas.isEmpty() || req.allowedAreas.contains(it.type) }
                .map { area ->
                    val lookup = area.path.lookup(gatewaySwitch)
                    PickerRow(
                        item = PickerItem(
                            dataArea = area,
                            lookup = lookup,
                            parent = null,
                            selected = selected.any { it.lookedUp == area.path },
                            selectable = true,
                        ),
                    )
                }

            else -> navState.last().lookup.lookupFiles(gatewaySwitch)
                .sortedWith(compareByDescending<APathLookup<*>> { it.isDirectory }.thenBy { it.name })
                .filter {
                    when (req.mode) {
                        PickerRequest.PickMode.DIR,
                        PickerRequest.PickMode.DIRS -> it.isDirectory
                    }
                }
                .map { lookup ->
                    val area = areaState.areas
                        .sortedByDescending { it.path.segments.size }
                        .first { it.path.isAncestorOf(lookup.lookedUp) }
                    PickerRow(
                        item = PickerItem(
                            dataArea = area,
                            lookup = lookup,
                            parent = current,
                            selected = selected.any { it.lookedUp == lookup.lookedUp },
                            selectable = true,
                        ),
                    )
                }
        }

        val selectedRows = selected.reversed().map { SelectedRow(lookup = it) }

        emit(
            State(
                current = current,
                items = items,
                selected = selectedRows,
                hasChanges = selected.map { it.lookedUp } != req.selectedPaths,
                progress = null,
            ),
        )
    }.replayingShare(vmScope)

    val state: StateFlow<State> = internalState.stateIn(
        scope = vmScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = State(),
    )

    fun onRowClick(row: PickerRow) {
        log(TAG) { "onRowClick($row)" }
        val newNav = (navigationState.value ?: emptyList()) + row.item
        navigationState.value = newNav
        persistNav(newNav)
    }

    fun onToggleSelect(row: PickerRow) {
        select(listOf(row.item.lookup))
    }

    fun onRemoveSelected(row: SelectedRow) {
        select(listOf(row.lookup))
    }

    fun selectAll() = launch {
        log(TAG) { "selectAll()" }
        val toSelect = internalState.first().items
            .map { it.item.lookup }
            .sortedByDescending { it.lookedUp.segments.size }
        select(toSelect)
    }

    fun home() {
        log(TAG) { "home()" }
        navigationState.value = null
        persistNav(null)
    }

    /**
     * Dismiss the picker, asking for confirmation first if the selection has diverged from the
     * originally-requested paths.
     */
    fun cancel(confirmed: Boolean = false) {
        log(TAG) { "cancel(confirmed=$confirmed)" }
        if (!confirmed && state.value.hasChanges) {
            events.tryEmit(Event.ExitConfirmation)
            return
        }
        clearPersisted()
        navCtrl.up()
    }

    /**
     * Hardware-back handling: step out one navigation level, or trigger [cancel] at the root.
     */
    fun goBack() {
        log(TAG) { "goBack()" }
        val cur = navigationState.value
        if (cur != null) {
            val newNav = cur.dropLast(1).takeIf { it.isNotEmpty() }
            navigationState.value = newNav
            persistNav(newNav)
        } else {
            cancel()
        }
    }

    fun select(paths: Collection<APathLookup<*>>) {
        log(TAG) { "select($paths)" }
        val current = selectedItems.value
        val mode = requestFlow.value?.mode ?: return
        val newSelectedItems = when (mode) {
            PickerRequest.PickMode.DIR -> {
                // Single-selection: tap to pick, tap again to clear.
                val path = paths.firstOrNull() ?: return
                val alreadySelected = current.any { it.lookedUp == path.lookedUp }
                if (alreadySelected) emptyList() else listOf(path)
            }

            PickerRequest.PickMode.DIRS -> current.toMutableList().apply {
                paths.forEach { path ->
                    val removed = removeIf { it.lookedUp == path.lookedUp }
                    if (!removed) {
                        removeIf { path.isAncestorOf(it) }
                        removeIf { path.isDescendantOf(it) }
                        add(path)
                    }
                }
            }
        }
        selectedItems.value = newSelectedItems
        persistSelected(newSelectedItems)
    }

    fun save() {
        log(TAG) { "save()" }
        val req = requestFlow.value ?: return
        val result = PickerResult(
            selectedPaths = selectedItems.value.map { it.lookedUp }.toSet(),
        )
        navCtrl.setResult(PickerResultKey(req.requestKey), result)
        clearPersisted()
        navUp()
    }

    private fun clearPersisted() {
        handle.remove<ArrayList<APath>>(KEY_SELECTED)
        handle.remove<ArrayList<APath>>(KEY_NAV_PATH)
    }

    data class PickerRow(val item: PickerItem) {
        val id: String get() = item.lookup.lookedUp.path
    }

    data class SelectedRow(val lookup: APathLookup<*>) {
        val id: String get() = lookup.lookedUp.path
    }

    data class State(
        val current: PickerItem? = null,
        val items: List<PickerRow> = emptyList(),
        val selected: List<SelectedRow> = emptyList(),
        val hasChanges: Boolean = false,
        val progress: Progress.Data? = Progress.Data(),
    )

    sealed interface Event {
        data object ExitConfirmation : Event
    }

    companion object {
        private val TAG = logTag("Common", "Picker", "ViewModel")
        private const val KEY_SELECTED = "picker.selectedPaths"
        private const val KEY_NAV_PATH = "picker.navigationPath"
    }
}
