package eu.darken.sdmse.squeezer.ui.list

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.previews.PreviewOptions
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.squeezer.core.CompressibleMedia
import eu.darken.sdmse.squeezer.core.Squeezer
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.core.hasData
import eu.darken.sdmse.squeezer.core.tasks.SqueezerProcessTask
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class SqueezerListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val squeezer: Squeezer,
    private val settings: SqueezerSettings,
    private val taskManager: TaskManager,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider) {

    init {
        squeezer.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<SqueezerListEvents>()

    val layoutMode: LayoutMode
        get() = settings.layoutMode.valueBlocking

    val state = combine(
        squeezer.state.map { it.data }.filterNotNull(),
        squeezer.progress,
        settings.layoutMode.flow,
        settings.compressionQuality.flow,
    ) { data, progress, layoutMode, quality ->
        val rows = data.images
            .sortedByDescending { it.size }
            .map { image ->
                when (layoutMode) {
                    LayoutMode.LINEAR -> SqueezerListLinearVH.Item(
                        image = image,
                        onItemClicked = { compress(setOf(it)) },
                        onPreviewClicked = { item ->
                            val options = PreviewOptions(paths = listOf(item.image.path))
                            events.postValue(SqueezerListEvents.PreviewEvent(options))
                        },
                    )

                    LayoutMode.GRID -> SqueezerListGridVH.Item(
                        image = image,
                        onItemClicked = { compress(setOf(it)) },
                        onPreviewClicked = { item ->
                            val options = PreviewOptions(paths = listOf(item.image.path))
                            events.postValue(SqueezerListEvents.PreviewEvent(options))
                        },
                    )
                }
            }
        State(rows, progress, layoutMode, quality)
    }.asLiveData2()

    data class State(
        val items: List<SqueezerListAdapter.Item>,
        val progress: Progress.Data? = null,
        val layoutMode: LayoutMode,
        val quality: Int,
    )

    fun compressAll() = launch {
        log(TAG, INFO) { "compressAll()" }
        val items = state.value?.items ?: return@launch
        if (items.isEmpty()) return@launch
        compress(items)
    }

    fun compress(
        items: Collection<SqueezerListAdapter.Item>,
        confirmed: Boolean = false,
        qualityOverride: Int? = null,
    ) = launch {
        log(TAG, INFO) { "compress(): ${items.size} confirmed=$confirmed" }

        val quality = qualityOverride ?: settings.compressionQuality.value()

        if (!confirmed) {
            val event = SqueezerListEvents.ConfirmCompression(items, quality)
            events.postValue(event)
            return@launch
        }

        if (!upgradeRepo.isPro()) {
            MainDirections.goToUpgradeFragment().navigate()
            return@launch
        }

        val mode: SqueezerProcessTask.TargetMode = SqueezerProcessTask.TargetMode.Selected(
            targets = items.map { it.image.identifier }.toSet(),
        )

        taskManager.submit(SqueezerProcessTask(mode = mode, qualityOverride = qualityOverride))
    }

    fun exclude(items: Collection<SqueezerListAdapter.Item>) = launch {
        log(TAG, INFO) { "exclude(): ${items.size}" }
        val targets = items.map { it.image.identifier }
        squeezer.exclude(targets)
        events.postValue(SqueezerListEvents.ExclusionsCreated(items.size))
    }

    fun toggleLayoutMode() = launch {
        log(TAG) { "toggleLayoutMode()" }
        when (settings.layoutMode.value()) {
            LayoutMode.LINEAR -> settings.layoutMode.value(LayoutMode.GRID)
            LayoutMode.GRID -> settings.layoutMode.value(LayoutMode.LINEAR)
        }
    }

    companion object {
        private val TAG = logTag("Squeezer", "List", "ViewModel")
    }
}
