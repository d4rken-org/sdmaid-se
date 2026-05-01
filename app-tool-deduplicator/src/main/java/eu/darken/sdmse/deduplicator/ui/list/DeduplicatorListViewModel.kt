package eu.darken.sdmse.deduplicator.ui.list

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
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.hasData
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorTask
import eu.darken.sdmse.deduplicator.ui.DeduplicatorDetailsRoute
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.main.core.taskmanager.getLatestTask
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DeduplicatorListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val deduplicator: Deduplicator,
    private val settings: DeduplicatorSettings,
    private val taskSubmitter: TaskSubmitter,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider = dispatcherProvider, tag = TAG) {

    init {
        deduplicator.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { navUp() }
            .launchInViewModel()

        val start = Instant.now()
        val handledResults = mutableSetOf<String>()
        taskSubmitter.state
            .map { it.getLatestTask(SDMTool.Type.DEDUPLICATOR) }
            .filterNotNull()
            .filter { it.completedAt!! > start }
            .onEach { task ->
                val result = task.result as? DeduplicatorTask.Result ?: return@onEach
                if (handledResults.add(task.id)) {
                    events.tryEmit(Event.TaskResult(result))
                }
            }
            .launchInViewModel()
    }

    val events = SingleEventFlow<Event>()

    val state: StateFlow<State?> = combine(
        deduplicator.state.map { it.data }.filterNotNull(),
        deduplicator.progress,
        settings.layoutMode.flow,
    ) { data, progress, layoutMode ->
        val rows = data.clusters
            .sortedByDescending { it.averageSize }
            .map { cluster ->
                val deleteTargetIds = run {
                    val favId = cluster.favoriteGroupIdentifier
                    cluster.groups.flatMap { group ->
                        if (group.identifier == favId) {
                            val keeper = group.keeperIdentifier
                            if (keeper != null) {
                                group.duplicates.filter { it.identifier != keeper }.map { it.identifier }
                            } else {
                                emptyList()
                            }
                        } else {
                            group.duplicates.map { it.identifier }
                        }
                    }.toSet()
                }
                DeduplicatorListRow(cluster = cluster, deleteTargetIds = deleteTargetIds)
            }
        State(rows = rows, progress = progress, layoutMode = layoutMode)
    }.safeStateIn(
        initialValue = null,
        onError = { null },
    )

    data class DeduplicatorListRow(
        val cluster: Duplicate.Cluster,
        val deleteTargetIds: Set<Duplicate.Id>,
    )

    data class State(
        val rows: List<DeduplicatorListRow>,
        val progress: Progress.Data? = null,
        val layoutMode: LayoutMode,
    )

    fun showDetails(id: Duplicate.Cluster.Id) {
        log(TAG, INFO) { "showDetails(id=$id)" }
        navTo(DeduplicatorDetailsRoute(identifier = id))
    }

    fun openPreview(options: PreviewOptions) {
        log(TAG, INFO) { "openPreview(options=$options)" }
        navTo(PreviewRoute(options = options))
    }

    fun previewCluster(cluster: Duplicate.Cluster) {
        val paths = cluster.groups.flatMap { gr -> gr.duplicates.map { it.path } }
        if (paths.isEmpty()) return
        openPreview(PreviewOptions(paths = paths))
    }

    fun previewDuplicate(cluster: Duplicate.Cluster, dupe: Duplicate) {
        val paths = cluster.groups.flatMap { gr -> gr.duplicates.map { it.path } }
        if (paths.isEmpty()) return
        openPreview(PreviewOptions(paths = paths, position = paths.indexOf(dupe.path)))
    }

    fun deleteClusters(clusters: Collection<Duplicate.Cluster>, confirmed: Boolean = false, deleteAll: Boolean = false) =
        launch {
            log(TAG, INFO) { "deleteClusters(${clusters.size}) confirmed=$confirmed deleteAll=$deleteAll" }

            val sanitized = clusters.distinctBy { it.identifier }
            if (sanitized.isEmpty()) return@launch

            if (!confirmed) {
                events.tryEmit(Event.ConfirmDeletion(sanitized, allowDeleteAll = settings.allowDeleteAll.value()))
                return@launch
            }

            if (!upgradeRepo.isPro()) {
                navTo(UpgradeRoute())
                return@launch
            }

            val mode = DeduplicatorDeleteTask.TargetMode.Clusters(
                targets = sanitized.map { it.identifier }.toSet(),
                deleteAll = deleteAll,
            )
            taskSubmitter.submit(DeduplicatorDeleteTask(mode = mode))
        }

    fun deleteDuplicate(cluster: Duplicate.Cluster, dupe: Duplicate, confirmed: Boolean = false) = launch {
        log(TAG, INFO) { "deleteDuplicate(${dupe.identifier}) confirmed=$confirmed" }

        if (!confirmed) {
            events.tryEmit(Event.ConfirmDupeDeletion(cluster, listOf(dupe)))
            return@launch
        }

        if (!upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }

        val mode = DeduplicatorDeleteTask.TargetMode.Duplicates(targets = setOf(dupe.identifier))
        taskSubmitter.submit(DeduplicatorDeleteTask(mode = mode))
    }

    fun excludeClusters(clusters: Collection<Duplicate.Cluster>) = launch {
        log(TAG, INFO) { "excludeClusters(${clusters.size})" }
        val sanitized = clusters.distinctBy { it.identifier }
        if (sanitized.isEmpty()) return@launch
        deduplicator.exclude(sanitized.map { it.identifier }.toSet())
        events.tryEmit(Event.ExclusionsCreated(count = sanitized.sumOf { it.count }))
    }

    fun toggleLayoutMode() = launch {
        log(TAG, INFO) { "toggleLayoutMode()" }
        when (settings.layoutMode.value()) {
            LayoutMode.LINEAR -> settings.layoutMode.value(LayoutMode.GRID)
            LayoutMode.GRID -> settings.layoutMode.value(LayoutMode.LINEAR)
        }
    }

    sealed interface Event {
        data class ConfirmDeletion(
            val clusters: Collection<Duplicate.Cluster>,
            val allowDeleteAll: Boolean,
        ) : Event

        data class ConfirmDupeDeletion(
            val cluster: Duplicate.Cluster,
            val duplicates: Collection<Duplicate>,
        ) : Event

        data class TaskResult(val result: DeduplicatorTask.Result) : Event
        data class ExclusionsCreated(val count: Int) : Event
    }

    companion object {
        private val TAG = logTag("Deduplicator", "List", "ViewModel")
    }
}
