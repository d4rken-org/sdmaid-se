package eu.darken.sdmse.appcleaner.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.hasData
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import java.lang.Integer.min
import javax.inject.Inject

@HiltViewModel
class AppJunkDetailsViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    appCleaner: AppCleaner,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args by handle.navArgs<AppJunkDetailsFragmentArgs>()

    private var currentTarget: Installed.InstallId? = null
        get() = field ?: handle["target"]
        set(value) {
            field = value.also { handle["target"] = it }
        }
    private var lastPosition: Int? = null
        get() = field ?: handle["position"]
        set(value) {
            field = value.also { handle["position"] = it }
        }

    init {
        appCleaner.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<AppJunkDetailsEvents>()

    val state = combine(
        appCleaner.progress,
        appCleaner.state
            .map { it.data }
            .filterNotNull()
            .distinctUntilChangedBy { junks -> junks.junks.map { it.identifier }.toSet() },
    ) { progress, newData ->
        val newJunks = newData.junks.sortedByDescending { it.size }

        val requestedTarget = currentTarget ?: args.identifier

        // Target still within the data set?
        val currentIndex = newJunks.indexOfFirst { it.identifier == requestedTarget }
        if (currentIndex != -1) lastPosition = currentIndex

        // If the target is no longer with us, use the new item that is now at the same position
        val availableTarget = when {
            currentIndex != -1 -> requestedTarget
            lastPosition != null -> newJunks[min(lastPosition!!, newJunks.size - 1)].identifier
            else -> requestedTarget
        }

        State(
            items = newJunks,
            target = availableTarget,
            progress = progress,
        )
    }.asLiveData2()

    data class State(
        val items: List<AppJunk>,
        val target: Installed.InstallId?,
        val progress: Progress.Data?,
    )

    fun updatePage(identifier: Installed.InstallId) {
        log(TAG) { "updatePage($identifier)" }
        currentTarget = identifier
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Details", "ViewModel")
    }
}