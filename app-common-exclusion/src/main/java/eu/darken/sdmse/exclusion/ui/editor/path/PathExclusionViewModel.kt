package eu.darken.sdmse.exclusion.ui.editor.path

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.NavigationController
import eu.darken.sdmse.common.picker.PickerRequest
import eu.darken.sdmse.common.picker.PickerResultKey
import eu.darken.sdmse.common.picker.PickerRoute
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.current
import eu.darken.sdmse.exclusion.core.remove
import eu.darken.sdmse.exclusion.core.save
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class PathExclusionViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val exclusionManager: ExclusionManager,
    private val gatewaySwitch: GatewaySwitch,
    private val navCtrl: NavigationController,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    val events = SingleEventFlow<Event>()

    private data class RouteArgs(val exclusionId: String?, val initial: PathExclusionEditorOptions?)

    private val routeFlow = MutableStateFlow<RouteArgs?>(null)
    private val currentFlow = MutableStateFlow<PathExclusion?>(null)
    private val originalFlow = MutableStateFlow<PathExclusion?>(null)
    private val lookupFlow = MutableStateFlow<APathLookup<*>?>(null)
    private val fatalFlow = MutableStateFlow(false)

    init {
        navCtrl.consumeResults(PickerResultKey(PICKER_REQUEST_KEY))
            .onEach { result ->
                log(TAG) { "Picker returned ${result.selectedPaths.size} paths" }
                result.selectedPaths.firstOrNull()?.let { updatePath(it) }
            }
            .launchIn(vmScope)
    }

    fun setArgs(exclusionId: String?, initial: PathExclusionEditorOptions?) {
        if (routeFlow.value != null) return
        if (exclusionId == null && initial == null) {
            log(TAG, WARN) { "setArgs: neither exclusionId nor initial options were provided" }
            fatalFlow.value = true
            return
        }
        log(TAG, INFO) { "setArgs($exclusionId, $initial)" }
        routeFlow.value = RouteArgs(exclusionId, initial)
        launch { hydrate() }
    }

    private suspend fun hydrate() {
        val args = routeFlow.value ?: return
        val identifier: ExclusionId = args.exclusionId ?: PathExclusion.createId(args.initial!!.targetPath)
        val orig = exclusionManager.current().singleOrNull { it.id == identifier } as PathExclusion?

        if (orig == null && args.initial == null) {
            log(TAG, WARN) { "hydrate: exclusion $identifier not found and no initial provided" }
            fatalFlow.value = true
            return
        }

        val initial = orig ?: PathExclusion(
            path = args.initial!!.targetPath,
            tags = setOf(Exclusion.Tag.GENERAL),
        )
        originalFlow.value = orig
        currentFlow.value = initial
        lookupFlow.value = safeLookup(initial.path)
    }

    private suspend fun safeLookup(path: APath): APathLookup<*>? = try {
        gatewaySwitch.lookup(path)
    } catch (e: Exception) {
        log(TAG, VERBOSE) { "Path exclusion lookup failed: $e" }
        null
    }

    val state: StateFlow<State> = fatalFlow
        .flatMapLatest { fatal ->
            if (fatal) flow { emit(State.NotFound) }
            else combine(currentFlow, originalFlow, lookupFlow) { current, original, lookup ->
                if (current == null) State.Loading
                else State.Ready(
                    current = current,
                    lookup = lookup,
                    canSave = current != original,
                    canRemove = original != null,
                )
            }
        }
        .safeStateIn(
            initialValue = State.Loading,
            onError = { State.NotFound },
        )

    fun toggleTag(tag: Exclusion.Tag) = launch {
        val old = currentFlow.value ?: return@launch
        log(TAG) { "toggleTag($tag)" }
        val allTags = Exclusion.Tag.values().toSet()
        val allTools = allTags.minus(Exclusion.Tag.GENERAL)

        var newTags = when {
            old.tags.contains(tag) -> old.tags.minus(tag)
            else -> old.tags.minus(Exclusion.Tag.GENERAL).plus(tag)
        }

        if (newTags.contains(Exclusion.Tag.GENERAL) || newTags == allTags || newTags == allTools) {
            newTags = setOf(Exclusion.Tag.GENERAL)
        }
        currentFlow.value = old.copy(tags = newTags)
    }

    fun editPath() = launch {
        val current = currentFlow.value ?: return@launch
        log(TAG) { "editPath()" }
        val request = PickerRequest(
            requestKey = PICKER_REQUEST_KEY,
            mode = PickerRequest.PickMode.DIR,
            selectedPaths = listOf(current.path),
        )
        navTo(PickerRoute(request = request))
    }

    fun updatePath(newPath: APath) = launch {
        val old = currentFlow.value ?: return@launch
        log(TAG) { "updatePath($newPath)" }
        currentFlow.value = old.copy(path = newPath)
        lookupFlow.value = safeLookup(newPath)
    }

    fun save() = launch {
        val snap = state.value as? State.Ready ?: return@launch
        log(TAG) { "save()" }
        var toSave = snap.current
        val origId = originalFlow.value?.id

        if (origId != null && origId != snap.current.id) {
            log(TAG) { "Path changed, removing old exclusion: $origId" }
            val existingForNewPath = exclusionManager.current()
                .filterIsInstance<PathExclusion>()
                .singleOrNull { it.id == snap.current.id }

            if (existingForNewPath != null) {
                log(TAG) { "Merging with existing exclusion: $existingForNewPath" }
                val mergedTags = snap.current.tags + existingForNewPath.tags
                toSave = snap.current.copy(tags = mergedTags)
            }

            exclusionManager.remove(origId)
        }

        exclusionManager.save(toSave)
        navUp()
    }

    fun remove(confirmed: Boolean = false) = launch {
        val snap = state.value as? State.Ready ?: return@launch
        log(TAG) { "remove() confirmed=$confirmed" }
        if (!snap.canRemove) return@launch

        if (!confirmed) {
            events.emit(Event.RemoveConfirmation)
        } else {
            exclusionManager.remove(snap.current.id)
            navUp()
        }
    }

    fun cancel(confirmed: Boolean = false) = launch {
        log(TAG) { "cancel() confirmed=$confirmed" }
        val snap = state.value as? State.Ready
        if (snap != null && snap.canSave && !confirmed) {
            events.emit(Event.UnsavedChangesConfirmation)
        } else {
            navUp()
        }
    }

    sealed interface State {
        data object Loading : State
        data object NotFound : State
        data class Ready(
            val current: PathExclusion,
            val lookup: APathLookup<*>?,
            val canSave: Boolean,
            val canRemove: Boolean,
        ) : State
    }

    sealed interface Event {
        data object RemoveConfirmation : Event
        data object UnsavedChangesConfirmation : Event
    }

    companion object {
        internal const val PICKER_REQUEST_KEY = "PathExclusionViewModel.picker"
        private val TAG = logTag("Exclusion", "Editor", "Path", "ViewModel")
    }
}
