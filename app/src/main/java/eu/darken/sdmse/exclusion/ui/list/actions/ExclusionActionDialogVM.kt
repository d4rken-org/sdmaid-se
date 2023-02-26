package eu.darken.sdmse.exclusion.ui.list.actions

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import kotlinx.coroutines.flow.*
import javax.inject.Inject


@HiltViewModel
class ExclusionActionDialogVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val exclusionManager: ExclusionManager,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<ExclusionActionDialogArgs>()
    private val identifier: String = navArgs.identifier

    init {
        exclusionManager.exclusions
            .map { data -> data.singleOrNull { it.id == identifier } }
            .filter { it == null }
            .take(1)
            .onEach {
                log(TAG) { "Exclusion $identifier is no longer available" }
                popNavStack()
            }
            .launchInViewModel()
    }

    val state = exclusionManager.exclusions
        .map { data -> data.singleOrNull { it.id == identifier } }
        .filterNotNull()
        .map {
            State(
                exclusion = it
            )
        }
        .asLiveData2()

    fun delete() = launch {
        log(TAG) { "delete()" }
        exclusionManager.remove(identifier)
    }

    data class State(
        val exclusion: Exclusion
    )

    companion object {
        private val TAG = logTag("Exclusion", "List", "Action", "VM")
    }

}