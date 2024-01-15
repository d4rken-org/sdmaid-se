package eu.darken.sdmse.deduplicator.ui.list

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.hasData
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class DeduplicatorListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val deduplicator: Deduplicator,
    private val settings: DeduplicatorSettings,
    private val taskManager: TaskManager,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider) {

    init {
        deduplicator.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<DeduplicatorListEvents>()

    val layoutMode: LayoutMode
        get() = settings.layoutMode.valueBlocking

    val state = combine(
        deduplicator.state.map { it.data }.filterNotNull(),
        deduplicator.progress,
        settings.layoutMode.flow,
    ) { data, progress, layoutMode ->
        val rows = data.clusters
            .sortedByDescending { it.averageSize }
            .map { cluster ->
                when (layoutMode) {
                    LayoutMode.LINEAR -> DeduplicatorListLinearVH.Item(
                        cluster = cluster,
                        onItemClicked = { delete(setOf(it)) },
                        onDupeClicked = { delete(setOf(it)) },
                        onPreviewClicked = { events.postValue(DeduplicatorListEvents.PreviewEvent(it.cluster.previewFile.lookedUp)) }
                    )

                    LayoutMode.GRID -> DeduplicatorListGridVH.Item(
                        cluster = cluster,
                        onItemClicked = { delete(setOf(it)) },
                        onFooterClicked = { showDetails(cluster.identifier) },
                    )
                }
            }
        State(rows, progress, layoutMode)
    }.asLiveData2()

    data class State(
        val items: List<DeduplicatorListAdapter.Item>,
        val progress: Progress.Data? = null,
        val layoutMode: LayoutMode,
    )

    fun delete(
        items: Collection<DeduplicatorListLinearSubAdapter.Item>,
        confirmed: Boolean = false,
    ) = launch {
        log(TAG, INFO) { "delete(): ${items.size} confirmed=$confirmed" }

        if (!confirmed) {
            val event = DeduplicatorListEvents.ConfirmDupeDeletion(items)
            events.postValue(event)
            return@launch
        }

        if (!upgradeRepo.isPro()) {
            MainDirections.goToUpgradeFragment().navigate()
            return@launch
        }

        val mode: DeduplicatorDeleteTask.TargetMode = DeduplicatorDeleteTask.TargetMode.Duplicates(
            targets = items.map { it.dupe.identifier }.toSet(),
        )

        taskManager.submit(DeduplicatorDeleteTask(mode = mode))
    }

    fun delete(
        items: Collection<DeduplicatorListAdapter.Item>,
        confirmed: Boolean = false,
        deleteAll: Boolean = false,
    ) = launch {
        log(TAG, INFO) { "delete(): ${items.size} confirmed=$confirmed" }

        if (!confirmed) {
            val event = DeduplicatorListEvents.ConfirmDeletion(items, settings.allowDeleteAll.value())
            events.postValue(event)
            return@launch
        }

        if (!upgradeRepo.isPro()) {
            MainDirections.goToUpgradeFragment().navigate()
            return@launch
        }

        val mode: DeduplicatorDeleteTask.TargetMode = DeduplicatorDeleteTask.TargetMode.Clusters(
            targets = items.map { it.cluster.identifier }.toSet(),
            deleteAll = deleteAll,
        )

        taskManager.submit(DeduplicatorDeleteTask(mode = mode))
    }

    fun exclude(items: Collection<DeduplicatorListAdapter.Item>) = launch {
        log(TAG, INFO) { "exclude(): ${items.size}" }
        val targets = items.map { it.cluster.identifier }.toSet()
        deduplicator.exclude(targets)
        events.postValue(DeduplicatorListEvents.ExclusionsCreated(items.sumOf { it.cluster.count }))
    }

    fun showDetails(id: Duplicate.Cluster.Id) = launch {
        log(TAG, INFO) { "showDetails(id=$id)" }
        DeduplicatorListFragmentDirections.actionDeduplicatorListFragmentToDeduplicatorDetailsFragment(
            identifier = id
        ).navigate()
    }

    fun toggleLayoutMode() = launch {
        log(TAG) { "toggleLayoutMode()" }
        when (settings.layoutMode.value()) {
            LayoutMode.LINEAR -> settings.layoutMode.value(LayoutMode.GRID)
            LayoutMode.GRID -> settings.layoutMode.value(LayoutMode.LINEAR)
        }
    }

    companion object {
        private val TAG = logTag("Deduplicator", "DuplicateGroupList", "ViewModel")
    }
}