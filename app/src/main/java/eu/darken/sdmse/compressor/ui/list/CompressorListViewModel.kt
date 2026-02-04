package eu.darken.sdmse.compressor.ui.list

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
import eu.darken.sdmse.compressor.core.CompressibleMedia
import eu.darken.sdmse.compressor.core.Compressor
import eu.darken.sdmse.compressor.core.CompressorSettings
import eu.darken.sdmse.compressor.core.hasData
import eu.darken.sdmse.compressor.core.tasks.CompressorProcessTask
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class CompressorListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val compressor: Compressor,
    private val settings: CompressorSettings,
    private val taskManager: TaskManager,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider) {

    init {
        compressor.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<CompressorListEvents>()

    val layoutMode: LayoutMode
        get() = settings.layoutMode.valueBlocking

    val state = combine(
        compressor.state.map { it.data }.filterNotNull(),
        compressor.progress,
        settings.layoutMode.flow,
        settings.compressionQuality.flow,
    ) { data, progress, layoutMode, quality ->
        val rows = data.images
            .sortedByDescending { it.size }
            .map { image ->
                when (layoutMode) {
                    LayoutMode.LINEAR -> CompressorListLinearVH.Item(
                        image = image,
                        onItemClicked = { compress(setOf(it)) },
                        onPreviewClicked = { item ->
                            val options = PreviewOptions(paths = listOf(item.image.path))
                            events.postValue(CompressorListEvents.PreviewEvent(options))
                        },
                    )

                    LayoutMode.GRID -> CompressorListGridVH.Item(
                        image = image,
                        onItemClicked = { compress(setOf(it)) },
                        onPreviewClicked = { item ->
                            val options = PreviewOptions(paths = listOf(item.image.path))
                            events.postValue(CompressorListEvents.PreviewEvent(options))
                        },
                    )
                }
            }
        State(rows, progress, layoutMode, quality)
    }.asLiveData2()

    data class State(
        val items: List<CompressorListAdapter.Item>,
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
        items: Collection<CompressorListAdapter.Item>,
        confirmed: Boolean = false,
        qualityOverride: Int? = null,
    ) = launch {
        log(TAG, INFO) { "compress(): ${items.size} confirmed=$confirmed" }

        val quality = qualityOverride ?: settings.compressionQuality.value()

        if (!confirmed) {
            val event = CompressorListEvents.ConfirmCompression(items, quality)
            events.postValue(event)
            return@launch
        }

        if (!upgradeRepo.isPro()) {
            MainDirections.goToUpgradeFragment().navigate()
            return@launch
        }

        val mode: CompressorProcessTask.TargetMode = CompressorProcessTask.TargetMode.Selected(
            targets = items.map { it.image.identifier }.toSet(),
        )

        taskManager.submit(CompressorProcessTask(mode = mode, qualityOverride = qualityOverride))
    }

    fun exclude(items: Collection<CompressorListAdapter.Item>) = launch {
        log(TAG, INFO) { "exclude(): ${items.size}" }
        val targets = items.map { it.image.identifier }
        compressor.exclude(targets)
        events.postValue(CompressorListEvents.ExclusionsCreated(items.size))
    }

    fun toggleLayoutMode() = launch {
        log(TAG) { "toggleLayoutMode()" }
        when (settings.layoutMode.value()) {
            LayoutMode.LINEAR -> settings.layoutMode.value(LayoutMode.GRID)
            LayoutMode.GRID -> settings.layoutMode.value(LayoutMode.LINEAR)
        }
    }

    companion object {
        private val TAG = logTag("Compressor", "List", "ViewModel")
    }
}
