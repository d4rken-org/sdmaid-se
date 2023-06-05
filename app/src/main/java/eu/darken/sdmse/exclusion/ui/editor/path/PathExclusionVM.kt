package eu.darken.sdmse.exclusion.ui.editor.path

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.currentExclusions
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import javax.inject.Inject


@HiltViewModel
class PathExclusionVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val exclusionManager: ExclusionManager,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<PathExclusionFragmentArgs>()
    private val identifier: ExclusionId? = navArgs.exclusionId
    private val initialOptions: PathExclusionEditorOptions? = navArgs.initial

    private val currentState = DynamicStateFlow<State>(TAG, viewModelScope) {
        val origExclusion = exclusionManager.currentExclusions()
            .singleOrNull { it.id == identifier } as PathExclusion?

        if (origExclusion == null && initialOptions == null) {
            throw IllegalArgumentException("Neither existing exclusion nor init options were available")
        }

        val excl = origExclusion ?: PathExclusion(
            path = initialOptions!!.targetPath,
            tags = setOf(Exclusion.Tag.GENERAL)
        )

        State(
            canRemove = origExclusion != null,
            canSave = origExclusion == null,
            exclusion = excl,
        )
    }

    val state = currentState.flow.asLiveData2()

    fun toggleTag(tag: Exclusion.Tag) = launch {
        log(TAG) { "toggleTag($tag)" }
        currentState.updateBlocking {
            val old = this.exclusion
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
            copy(
                exclusion = newExclusion,
                canSave = newExclusion != old,
            )
        }
    }

    fun save() = launch {
        log(TAG) { "save()" }
        exclusionManager.save(currentState.value().exclusion)
        popNavStack()
    }

    fun cancel() {
        log(TAG) { "cancel()" }
        popNavStack()
    }

    fun remove() = launch {
        val snap = currentState.value()
        log(TAG) { "remove() state=$snap" }
        if (!snap.canRemove) return@launch

        exclusionManager.remove(snap.exclusion.id)
        popNavStack()
    }

    data class State(
        val exclusion: PathExclusion,
        val canRemove: Boolean = false,
        val canSave: Boolean = false,
    )

    companion object {
        private val TAG = logTag("Exclusion", "List", "Action", "VM")
    }

}