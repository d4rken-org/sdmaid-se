package eu.darken.sdmse.squeezer.ui.list

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.previews.PreviewOptions
import eu.darken.sdmse.common.previews.PreviewRoute
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.squeezer.core.CompressibleMedia
import eu.darken.sdmse.squeezer.core.Squeezer
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.core.hasData
import eu.darken.sdmse.squeezer.core.tasks.SqueezerProcessTask
import eu.darken.sdmse.squeezer.core.tasks.SqueezerTask
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class SqueezerListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val squeezer: Squeezer,
    private val settings: SqueezerSettings,
    private val taskSubmitter: TaskSubmitter,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    init {
        squeezer.state
            .map { it.data }
            .filterNotNull()
            .filter { !it.hasData }
            .take(1)
            .onEach {
                log(TAG, INFO) { "Data drained, popping back stack" }
                navUp()
            }
            .launchIn(vmScope)
    }

    val events = SingleEventFlow<Event>()

    val state: StateFlow<State> = combine(
        squeezer.state.map { it.data },
        squeezer.progress,
        settings.layoutMode.flow,
        settings.compressionQuality.flow,
    ) { data, progress, layoutMode, quality ->
        State(
            media = data?.media?.sortedByDescending { it.size },
            progress = progress,
            layoutMode = layoutMode,
            quality = quality,
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    data class State(
        val media: List<CompressibleMedia>? = null,
        val progress: Progress.Data? = null,
        val layoutMode: LayoutMode = LayoutMode.LINEAR,
        val quality: Int = SqueezerSettings.DEFAULT_QUALITY,
    )

    sealed interface Event {
        data class ConfirmCompression(
            val items: List<CompressibleMedia>,
            val quality: Int,
        ) : Event

        data class ExclusionsCreated(val count: Int) : Event

        data class TaskResult(val result: SqueezerTask.Result) : Event
    }

    fun compressAll() = launch {
        log(TAG, INFO) { "compressAll()" }
        val media = state.value.media ?: return@launch
        if (media.isEmpty()) return@launch
        compress(media.map { it.identifier }.toSet())
    }

    fun compress(
        ids: Set<CompressibleMedia.Id>,
        confirmed: Boolean = false,
        qualityOverride: Int? = null,
    ) = launch {
        log(TAG, INFO) { "compress(): ${ids.size} confirmed=$confirmed" }

        val current = state.value.media ?: return@launch
        val items = current.filter { it.identifier in ids }
        if (items.isEmpty()) return@launch

        val quality = qualityOverride ?: settings.compressionQuality.value()

        if (!confirmed) {
            events.tryEmit(Event.ConfirmCompression(items, quality))
            return@launch
        }

        if (!upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }

        val mode: SqueezerProcessTask.TargetMode = SqueezerProcessTask.TargetMode.Selected(
            targets = ids,
        )

        val result = taskSubmitter.submit(
            SqueezerProcessTask(mode = mode, qualityOverride = qualityOverride),
        ) as SqueezerTask.Result
        events.tryEmit(Event.TaskResult(result))
    }

    fun exclude(ids: Set<CompressibleMedia.Id>) = launch {
        log(TAG, INFO) { "exclude(): ${ids.size}" }
        squeezer.exclude(ids)
        events.tryEmit(Event.ExclusionsCreated(ids.size))
    }

    fun toggleLayoutMode() = launch {
        log(TAG) { "toggleLayoutMode()" }
        when (settings.layoutMode.value()) {
            LayoutMode.LINEAR -> settings.layoutMode.value(LayoutMode.GRID)
            LayoutMode.GRID -> settings.layoutMode.value(LayoutMode.LINEAR)
        }
    }

    fun openPreview(media: CompressibleMedia) {
        log(TAG) { "openPreview(${media.identifier})" }
        navTo(PreviewRoute(options = PreviewOptions(paths = listOf(media.path))))
    }

    fun openExclusionsList() {
        log(TAG) { "openExclusionsList()" }
        navTo(ExclusionsListRoute)
    }

    companion object {
        private val TAG = logTag("Squeezer", "List", "ViewModel")
    }
}
