package eu.darken.sdmse.deduplicator.ui.details.cluster

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.ChecksumGroupFileVH
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.ChecksumGroupHeaderVH
import eu.darken.sdmse.deduplicator.ui.details.cluster.elements.ClusterHeaderVH
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
        settings.isKeepOneEnabled.flow,
    ) { cluster, progress, keepOne ->
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

                when (group) {
                    is ChecksumDuplicate.Group -> {
                        ChecksumGroupHeaderVH.Item(
                            group = group,
                            onItemClick = {
//                                TODO()
                                delete(setOf(it))
                            },
                            onViewActionClick = {
                                events.postValue(ClusterEvents.ViewItem(it.group.preview))
                            }
                        ).run { items.add(this) }
                        group.duplicates.map { dupe ->
                            ChecksumGroupFileVH.Item(
                                duplicate = dupe,
                                onItemClick = {
//                                    TODO()
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
            keepOne = keepOne,
        )
    }.asLiveData2()

    data class State(
        val elements: List<ClusterAdapter.Item>,
        val progress: Progress.Data? = null,
        val keepOne: Boolean = true,
    )

    fun delete(items: Collection<ClusterAdapter.Item>, confirmed: Boolean = false) = launch {
        log(TAG, INFO) { "delete(items=$items)" }
        if (!confirmed) {
            events.postValue(ClusterEvents.ConfirmDeletion(items))
            return@launch
        }

//        val targets = items.mapNotNull {
//            when (it) {
//                is HashGroupFileVH.Item -> it.lookup.lookedUp
//                else -> null
//            }
//        }.toSet()
//
//        val task = DeduplicatorDeleteTask(
//            targetDuplicates = setOf(clusterData.first().identifier),
//            targetGroups = targets.takeIf { it.isNotEmpty() }
//        )
//
//        taskManager.submit(task)
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
            .filterIsInstance<ClusterAdapter.FileItem>()
            .map { it.path }
            .takeIf { it.isNotEmpty() }
            ?.let { deduplicator.exclude(cluster.identifier, it) }
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Cluster", "ViewModel")
    }
}