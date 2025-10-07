package eu.darken.sdmse.deduplicator.ui.details.cluster

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.ViewIntentTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.previews.PreviewOptions
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.ChecksumGroupFileVH
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.ChecksumGroupHeaderVH
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.ClusterHeaderVH
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.PHashGroupFileVH
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.PHashGroupHeaderVH
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class ClusterViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val deduplicator: Deduplicator,
    private val settings: DeduplicatorSettings,
    private val taskManager: TaskManager,
    private val upgradeRepo: UpgradeRepo,
    private val viewIntentTool: ViewIntentTool,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args = ClusterFragmentArgs.fromSavedStateHandle(handle)

    val events = SingleLiveEvent<ClusterEvents>()

    private val clusterData = deduplicator.state
        .map { it.data }
        .filterNotNull()
        .map { data -> data.clusters.singleOrNull { it.identifier == args.identifier } }
        .filterNotNull()

    val state = combine(
        clusterData,
        deduplicator.progress,
        settings.allowDeleteAll.flow,
    ) { cluster, progress, allowDeleteAll ->
        val elements = mutableListOf<ClusterAdapter.Item>()

        ClusterHeaderVH.Item(
            cluster = cluster,
            onDeleteAllClicked = { delete(setOf(it)) },
            onExcludeClicked = { exclude(setOf(it)) },
        ).run { elements.add(this) }

        cluster.groups
            .sortedByDescending { it.totalSize }
            .flatMap { group ->
                val items = mutableListOf<ClusterAdapter.Item>()

                when (group.type) {
                    Duplicate.Type.CHECKSUM -> {
                        group as ChecksumDuplicate.Group
                        ChecksumGroupHeaderVH.Item(
                            group = group,
                            onItemClick = { delete(setOf(it)) },
                            onViewActionClick = { item ->
                                val options = PreviewOptions(
                                    paths = item.group.duplicates.map { it.path }
                                )
                                events.postValue(ClusterEvents.ViewDuplicate(options))
                            }
                        ).run { items.add(this) }
                        group.duplicates.map { dupe ->
                            ChecksumGroupFileVH.Item(
                                duplicate = dupe,
                                onItemClick = { delete(listOf(it)) }
                            )
                        }.run { items.addAll(this) }
                    }

                    Duplicate.Type.PHASH -> {
                        group as PHashDuplicate.Group
                        PHashGroupHeaderVH.Item(
                            group = group,
                            onItemClick = { delete(setOf(it)) },
                            onViewActionClick = { item ->
                                val paths = group.duplicates.map { it.path }
                                val options = PreviewOptions(
                                    paths = paths,
                                    position = paths.indexOf(item.path)
                                )
                                events.postValue(ClusterEvents.ViewDuplicate(options))
                            }
                        ).run { items.add(this) }
                        group.duplicates.map { dupe ->
                            PHashGroupFileVH.Item(
                                duplicate = dupe,
                                onItemClick = { delete(listOf(it)) },
                                onPreviewClick = { item ->
                                    val paths = group.duplicates.map { it.path }
                                    val options = PreviewOptions(
                                        paths = paths,
                                        position = paths.indexOf(item.path)
                                    )
                                    events.postValue(ClusterEvents.ViewDuplicate(options))
                                }
                            )
                        }.run { items.addAll(this) }
                    }
                }

                items
            }
            .run { elements.addAll(this) }

        State(
            elements = elements,
            progress = progress,
            allowDeleteAll = allowDeleteAll,
        )
    }.asLiveData2()

    data class State(
        val elements: List<ClusterAdapter.Item>,
        val progress: Progress.Data? = null,
        val allowDeleteAll: Boolean = false,
    )

    fun delete(
        items: Collection<ClusterAdapter.Item>,
        confirmed: Boolean = false,
        deleteAll: Boolean = false,
    ) = launch {
        log(TAG, INFO) { "delete(items=$items)" }

        if (!confirmed) {
            events.postValue(ClusterEvents.ConfirmDeletion(items, allowDeleteAll = settings.allowDeleteAll.value()))
            return@launch
        }

        if (!upgradeRepo.isPro()) {
            MainDirections.goToUpgradeFragment().navigate()
            return@launch
        }

        val mode: DeduplicatorDeleteTask.TargetMode = when {
            items.singleOrNull() is ClusterAdapter.ClusterItem -> DeduplicatorDeleteTask.TargetMode.Clusters(
                targets = setOf((items.single() as ClusterAdapter.ClusterItem).identifier),
                deleteAll = deleteAll,
            )

            items.singleOrNull() is ClusterAdapter.GroupItem -> DeduplicatorDeleteTask.TargetMode.Groups(
                targets = setOf((items.single() as ClusterAdapter.GroupItem).identifier),
                deleteAll = deleteAll,
            )

            items.all { it is ClusterAdapter.DuplicateItem } -> DeduplicatorDeleteTask.TargetMode.Duplicates(
                targets = items.map { (it as ClusterAdapter.DuplicateItem).identifier }.toSet()
            )

            else -> throw IllegalArgumentException("Unsupported items: $items")
        }

        val task = DeduplicatorDeleteTask(mode = mode)
        taskManager.submit(task)
    }

    fun exclude(items: Collection<ClusterAdapter.Item>) = launch {
        log(TAG, INFO) { "exclude(): $items" }
        val cluster = clusterData.first()

        items
            .filterIsInstance<ClusterHeaderVH.Item>()
            .singleOrNull()
            ?.let {
                deduplicator.exclude(setOf(it.cluster.identifier))
            }

        items
            .filterIsInstance<ClusterAdapter.DuplicateItem>()
            .map { it.path }
            .takeIf { it.isNotEmpty() }
            ?.let { deduplicator.exclude(cluster.identifier, it) }
    }

    fun open(item: ClusterAdapter.DuplicateItem) = launch {
        log(TAG, INFO) { "open(): $item" }
        val intent = viewIntentTool.create(item.duplicate.lookup)
        if (intent == null) {
            log(TAG, WARN) { "open(): Unable to create view intent for ${item.duplicate.lookup}" }
            return@launch
        }
        events.postValue(ClusterEvents.OpenDuplicate(intent))
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Cluster", "ViewModel")
    }
}