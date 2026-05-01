package eu.darken.sdmse.deduplicator.ui.details

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.ViewIntentTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.mutableState
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.previews.PreviewOptions
import eu.darken.sdmse.common.previews.PreviewRoute
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.uix.resolveTarget
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.hasData
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorTask
import eu.darken.sdmse.deduplicator.ui.DeduplicatorDetailsRoute
import eu.darken.sdmse.deduplicator.ui.details.cluster.DirectoryGroup
import eu.darken.sdmse.deduplicator.ui.dialogs.PreviewDeletionMode
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.main.core.taskmanager.getLatestTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import eu.darken.sdmse.common.flow.combine
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DeduplicatorDetailsViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val deduplicator: Deduplicator,
    private val taskSubmitter: TaskSubmitter,
    private val settings: DeduplicatorSettings,
    private val upgradeRepo: UpgradeRepo,
    private val viewIntentTool: ViewIntentTool,
) : ViewModel4(dispatcherProvider = dispatcherProvider, tag = TAG) {

    private val routeFlow = MutableStateFlow<DeduplicatorDetailsRoute?>(null)
    private var currentTarget: Duplicate.Cluster.Id? by handle.mutableState("target")
    private var lastPosition: Int? by handle.mutableState("position")

    private val collapsedDirsFlow = MutableStateFlow<Map<Duplicate.Cluster.Id, Set<DirectoryGroup.Id>>>(emptyMap())

    val events = SingleEventFlow<Event>()

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

    fun bindRoute(route: DeduplicatorDetailsRoute) {
        if (routeFlow.value != null) return
        log(TAG, INFO) { "bindRoute(${route.identifier})" }
        routeFlow.value = route
    }

    val state: StateFlow<State?> = routeFlow.filterNotNull().flatMapLatest { route ->
        combine(
            deduplicator.progress,
            deduplicator.state
                .map { it.data }
                .filterNotNull()
                .distinctUntilChangedBy { data -> data.clusters.map { it.identifier }.toSet() },
            settings.isDirectoryViewEnabled.flow,
            settings.allowDeleteAll.flow,
            collapsedDirsFlow,
        ) { progress, data, isDirectoryViewEnabled, allowDeleteAll, collapsedDirs ->
            val sortedClusters = data.clusters.sortedByDescending { it.averageSize }

            val availableTarget = resolveTarget(
                items = sortedClusters,
                requestedTarget = currentTarget ?: route.identifier,
                lastPosition = lastPosition,
                identifierOf = { it.identifier },
                onPositionTracked = { lastPosition = it },
            )

            // Prune stale collapsed-dir entries: cluster ids that no longer exist
            val liveClusterIds = sortedClusters.map { it.identifier }.toSet()
            val staleClusters = collapsedDirs.keys - liveClusterIds
            if (staleClusters.isNotEmpty()) {
                collapsedDirsFlow.value = collapsedDirs - staleClusters
            }

            State(
                items = sortedClusters,
                target = availableTarget,
                progress = progress,
                isDirectoryView = isDirectoryViewEnabled,
                allowDeleteAll = allowDeleteAll,
                collapsedDirs = collapsedDirs,
            )
        }
    }.safeStateIn(
        initialValue = null,
        onError = { null },
    )

    data class State(
        val items: List<Duplicate.Cluster>,
        val target: Duplicate.Cluster.Id?,
        val progress: Progress.Data?,
        val isDirectoryView: Boolean = false,
        val allowDeleteAll: Boolean = false,
        val collapsedDirs: Map<Duplicate.Cluster.Id, Set<DirectoryGroup.Id>> = emptyMap(),
    )

    fun updatePage(identifier: Duplicate.Cluster.Id) {
        log(TAG, INFO) { "updatePage($identifier)" }
        currentTarget = identifier
    }

    fun toggleDirectoryView() = launch {
        log(TAG, INFO) { "toggleDirectoryView()" }
        settings.isDirectoryViewEnabled.update { !it }
    }

    fun toggleDirectoryCollapse(clusterId: Duplicate.Cluster.Id, dirId: DirectoryGroup.Id) {
        log(TAG, INFO) { "toggleDirectoryCollapse($clusterId, $dirId)" }
        collapsedDirsFlow.update { current ->
            val perCluster = current[clusterId] ?: emptySet()
            val newSet = if (perCluster.contains(dirId)) perCluster - dirId else perCluster + dirId
            current + (clusterId to newSet)
        }
    }

    fun previewCluster(cluster: Duplicate.Cluster) {
        val paths = cluster.groups.flatMap { gr -> gr.duplicates.map { it.path } }
        if (paths.isEmpty()) return
        navTo(PreviewRoute(options = PreviewOptions(paths = paths)))
    }

    fun previewGroup(group: Duplicate.Group, position: Int = 0) {
        val paths = group.duplicates.map { it.path }
        if (paths.isEmpty()) return
        navTo(PreviewRoute(options = PreviewOptions(paths = paths, position = position.coerceIn(0, paths.lastIndex))))
    }

    fun previewDuplicate(duplicate: Duplicate) {
        navTo(PreviewRoute(options = PreviewOptions(paths = listOf(duplicate.path))))
    }

    fun openDuplicate(duplicate: Duplicate) = launch {
        log(TAG, INFO) { "openDuplicate(${duplicate.identifier})" }
        val intent = viewIntentTool.create(duplicate.lookup)
        if (intent == null) {
            log(TAG, WARN) { "openDuplicate(): no intent for ${duplicate.lookup}" }
            return@launch
        }
        events.tryEmit(Event.OpenDuplicate(intent))
    }

    fun deleteCluster(clusterId: Duplicate.Cluster.Id, confirmed: Boolean = false, deleteAll: Boolean = false) = launch {
        log(TAG, INFO) { "deleteCluster($clusterId) confirmed=$confirmed deleteAll=$deleteAll" }
        val cluster = currentClusterOrNull(clusterId) ?: return@launch

        if (!confirmed) {
            events.tryEmit(
                Event.ConfirmDeletion(
                    clusterId = clusterId,
                    target = DeleteTarget.ClusterTarget(clusterId),
                    mode = PreviewDeletionMode.Clusters(
                        clusters = listOf(cluster),
                        allowDeleteAll = settings.allowDeleteAll.value(),
                    ),
                )
            )
            return@launch
        }

        if (!upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }

        val mode = DeduplicatorDeleteTask.TargetMode.Clusters(
            targets = setOf(clusterId),
            deleteAll = deleteAll,
        )
        taskSubmitter.submit(DeduplicatorDeleteTask(mode = mode))
    }

    fun deleteGroup(
        clusterId: Duplicate.Cluster.Id,
        groupId: Duplicate.Group.Id,
        confirmed: Boolean = false,
        deleteAll: Boolean = false,
    ) = launch {
        log(TAG, INFO) { "deleteGroup($clusterId, $groupId) confirmed=$confirmed deleteAll=$deleteAll" }
        val cluster = currentClusterOrNull(clusterId) ?: return@launch
        val group = cluster.groups.firstOrNull { it.identifier == groupId } ?: return@launch

        if (!confirmed) {
            events.tryEmit(
                Event.ConfirmDeletion(
                    clusterId = clusterId,
                    target = DeleteTarget.GroupTarget(groupId),
                    mode = PreviewDeletionMode.Groups(
                        groups = listOf(group),
                        allowDeleteAll = settings.allowDeleteAll.value(),
                    ),
                )
            )
            return@launch
        }

        if (!upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }

        val mode = DeduplicatorDeleteTask.TargetMode.Groups(
            targets = setOf(groupId),
            deleteAll = deleteAll,
        )
        taskSubmitter.submit(DeduplicatorDeleteTask(mode = mode))
    }

    fun deleteDuplicates(
        clusterId: Duplicate.Cluster.Id,
        ids: Collection<Duplicate.Id>,
        confirmed: Boolean = false,
    ) = launch {
        log(TAG, INFO) { "deleteDuplicates($clusterId, ${ids.size}) confirmed=$confirmed" }
        val cluster = currentClusterOrNull(clusterId) ?: return@launch
        val liveDuplicates = cluster.groups.flatMap { it.duplicates }
        val targetDuplicates = liveDuplicates.filter { it.identifier in ids }
        if (targetDuplicates.isEmpty()) return@launch

        if (!confirmed) {
            events.tryEmit(
                Event.ConfirmDeletion(
                    clusterId = clusterId,
                    target = DeleteTarget.DuplicateTargets(targetDuplicates.map { it.identifier }.toSet()),
                    mode = PreviewDeletionMode.Duplicates(duplicates = targetDuplicates),
                )
            )
            return@launch
        }

        if (!upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }

        val mode = DeduplicatorDeleteTask.TargetMode.Duplicates(
            targets = targetDuplicates.map { it.identifier }.toSet(),
        )
        taskSubmitter.submit(DeduplicatorDeleteTask(mode = mode))
    }

    fun excludeCluster(clusterId: Duplicate.Cluster.Id) = launch {
        log(TAG, INFO) { "excludeCluster($clusterId)" }
        val cluster = currentClusterOrNull(clusterId) ?: return@launch
        val undo = deduplicator.exclude(setOf(cluster.identifier))
        events.tryEmit(
            Event.ExclusionsCreated(
                count = undo.exclusionIds.size,
                undo = undo,
                restoreTarget = clusterId,
            ),
        )
    }

    fun excludeDuplicates(clusterId: Duplicate.Cluster.Id, ids: Collection<Duplicate.Id>) = launch {
        log(TAG, INFO) { "excludeDuplicates($clusterId, ${ids.size})" }
        val cluster = currentClusterOrNull(clusterId) ?: return@launch
        val liveDuplicates = cluster.groups.flatMap { it.duplicates }
        val paths = liveDuplicates.filter { it.identifier in ids }.map { it.path }
        if (paths.isEmpty()) return@launch
        deduplicator.exclude(cluster.identifier, paths)
    }

    fun onUndoExclude(undo: Deduplicator.ExclusionUndo, restoreTarget: Duplicate.Cluster.Id) = launch {
        log(TAG, INFO) { "onUndoExclude(${undo.exclusionIds.size}, restore=$restoreTarget)" }
        currentTarget = restoreTarget
        deduplicator.undoExclude(undo)
    }

    private suspend fun currentClusterOrNull(clusterId: Duplicate.Cluster.Id): Duplicate.Cluster? {
        val data = deduplicator.state.first { it.data?.hasData == true }.data ?: return null
        return data.clusters.firstOrNull { it.identifier == clusterId }
    }

    sealed interface DeleteTarget {
        data class ClusterTarget(val id: Duplicate.Cluster.Id) : DeleteTarget
        data class GroupTarget(val id: Duplicate.Group.Id) : DeleteTarget
        data class DuplicateTargets(val ids: Set<Duplicate.Id>) : DeleteTarget
    }

    sealed interface Event {
        data class ConfirmDeletion(
            val clusterId: Duplicate.Cluster.Id,
            val target: DeleteTarget,
            val mode: PreviewDeletionMode,
        ) : Event

        data class OpenDuplicate(val intent: Intent) : Event
        data class TaskResult(val result: DeduplicatorTask.Result) : Event
        data class ExclusionsCreated(
            val count: Int,
            val undo: Deduplicator.ExclusionUndo,
            val restoreTarget: Duplicate.Cluster.Id,
        ) : Event
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Details", "ViewModel")
    }
}
