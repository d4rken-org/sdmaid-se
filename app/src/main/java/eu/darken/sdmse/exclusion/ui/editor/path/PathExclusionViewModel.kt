package eu.darken.sdmse.exclusion.ui.editor.path

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.picker.PickerRequest
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.current
import eu.darken.sdmse.exclusion.core.remove
import eu.darken.sdmse.exclusion.core.save
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import javax.inject.Inject


@HiltViewModel
class PathExclusionViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val exclusionManager: ExclusionManager,
    private val gatewaySwitch: GatewaySwitch,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<PathExclusionFragmentArgs>()
    private val initialOptions: PathExclusionEditorOptions? = navArgs.initial
    private val identifier: ExclusionId = navArgs.exclusionId ?: PathExclusion.createId(initialOptions!!.targetPath)

    val events = SingleLiveEvent<PathEditorEvents>()

    private val currentState = DynamicStateFlow(TAG, vmScope) {
        val origExclusion = exclusionManager.current().singleOrNull { it.id == identifier } as PathExclusion?

        if (origExclusion == null && initialOptions == null) {
            throw IllegalArgumentException("Neither existing exclusion nor init options were available")
        }

        val newExcl = origExclusion ?: PathExclusion(
            path = initialOptions!!.targetPath,
            tags = setOf(Exclusion.Tag.GENERAL)
        )

        val lookup = try {
            gatewaySwitch.lookup(newExcl.path)
        } catch (e: Exception) {
            log(TAG, VERBOSE) { "Path exclusion lookup failed: $e" }
            null
        }

        State(
            original = origExclusion,
            current = newExcl,
            lookup = lookup,
        )
    }

    val state = currentState.flow.asLiveData2()

    fun toggleTag(tag: Exclusion.Tag) = launch {
        log(TAG) { "toggleTag($tag)" }
        currentState.updateBlocking {
            val old = this.current
            val allTags = Exclusion.Tag.values().toSet()
            val allTools = allTags.minus(Exclusion.Tag.GENERAL)

            var newTags = when {
                old.tags.contains(tag) -> old.tags.minus(tag)
                else -> old.tags.minus(Exclusion.Tag.GENERAL).plus(tag)
            }

            if (newTags.contains(Exclusion.Tag.GENERAL) || newTags == allTags || newTags == allTools) {
                newTags = setOf(Exclusion.Tag.GENERAL)
            }

            val newExclusion = old.copy(tags = newTags)
            copy(current = newExclusion)
        }
    }

    fun editPath() = launch {
        log(TAG) { "editPath()" }
        val currentPath = currentState.value().current.path
        val request = PickerRequest(
            requestKey = PICKER_REQUEST_KEY,
            mode = PickerRequest.PickMode.DIR,
            selectedPaths = listOf(currentPath),
        )
        events.postValue(PathEditorEvents.LaunchPicker(request))
    }

    fun updatePath(newPath: APath) = launch {
        log(TAG) { "updatePath($newPath)" }
        currentState.updateBlocking {
            val newExclusion = current.copy(path = newPath)
            val newLookup = try {
                gatewaySwitch.lookup(newPath)
            } catch (e: Exception) {
                log(TAG, VERBOSE) { "Path exclusion lookup failed: $e" }
                null
            }
            copy(current = newExclusion, lookup = newLookup)
        }
    }

    fun save() = launch {
        log(TAG) { "save()" }
        val snap = currentState.value()

        var toSave = snap.current

        // If path changed (original exists with different ID), handle potential conflicts
        val origId = snap.original?.id
        if (origId != null && origId != snap.current.id) {
            log(TAG) { "Path changed, removing old exclusion: $origId" }

            // Check if an exclusion already exists for the new path and merge tags
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
        popNavStack()
    }

    fun remove(confirmed: Boolean = false) = launch {
        val snap = currentState.value()
        log(TAG) { "remove() state=$snap" }
        if (!snap.canRemove) return@launch

        if (!confirmed) {
            events.postValue(PathEditorEvents.RemoveConfirmation(snap.current))
        } else {
            exclusionManager.remove(snap.current.id)
            popNavStack()
        }
    }

    fun cancel(confirmed: Boolean = false) = launch {
        log(TAG) { "cancel()" }
        val snap = currentState.value()
        if (snap.canSave && !confirmed) {
            events.postValue(PathEditorEvents.UnsavedChangesConfirmation(snap.current))
        } else {
            popNavStack()
        }
    }

    data class State(
        val original: PathExclusion?,
        val current: PathExclusion,
        val lookup: APathLookup<*>?,
    ) {
        val canRemove: Boolean = original != null
        val canSave: Boolean = original != current
    }

    companion object {
        internal const val PICKER_REQUEST_KEY = "PathExclusionViewModel.picker"
        private val TAG = logTag("Exclusion", "Editor", "Path", "ViewModel")
    }

}