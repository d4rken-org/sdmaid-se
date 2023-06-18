package eu.darken.sdmse.appcleaner.ui.details.appjunk

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerDeleteTask
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementFileCategoryVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementFileVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementHeaderVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementInaccessibleVH
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AppJunkFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val appCleaner: AppCleaner,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args = AppJunkFragmentArgs.fromSavedStateHandle(handle)

    val events = SingleLiveEvent<AppJunkEvents>()

    private val currentAppJunk = appCleaner.data
        .filterNotNull()
        .map { data -> data.junks.singleOrNull { it.identifier == args.identifier } }
        .filterNotNull()

    val state = combine(currentAppJunk, appCleaner.progress) { junk, progress ->
        val items = mutableListOf<AppJunkElementsAdapter.Item>()

        AppJunkElementHeaderVH.Item(
            appJunk = junk,
            onDeleteAllClicked = {
                val deleteTask = AppCleanerDeleteTask(
                    setOf(junk.identifier),
                    includeInaccessible = true
                )
                events.postValue(AppJunkEvents.ConfirmDeletion(junk, deleteTask))
            },
            onExcludeClicked = { launch { appCleaner.exclude(junk.identifier) } },
        ).run { items.add(this) }

        junk.inaccessibleCache?.let {
            AppJunkElementInaccessibleVH.Item(
                appJunk = junk,
                inaccessibleCache = junk.inaccessibleCache,
                onItemClick = {
                    val deleteTask = AppCleanerDeleteTask(
                        setOf(junk.identifier),
                        includeInaccessible = true,
                        onlyInaccessible = true
                    )
                    events.postValue(AppJunkEvents.ConfirmDeletion(junk, deleteTask))
                }
            ).run { items.add(this) }
        }

        junk.expendables
            ?.filter { it.value.isNotEmpty() }
            ?.map { (category, paths) ->
                val categoryGroup = mutableListOf<AppJunkElementsAdapter.Item>()

                AppJunkElementFileCategoryVH.Item(
                    appJunk = junk,
                    category = category,
                    paths = paths,
                    onItemClick = {
                        val deleteTask = AppCleanerDeleteTask(
                            setOf(it.appJunk.identifier),
                            targetFilters = setOf(it.category),
                            includeInaccessible = false,
                        )
                        events.postValue(AppJunkEvents.ConfirmDeletion(junk, deleteTask))
                    },
                ).run { categoryGroup.add(this) }

                paths
                    .map { lookup ->
                        AppJunkElementFileVH.Item(
                            appJunk = junk,
                            category = category,
                            lookup = lookup,
                            onItemClick = {
                                val deleteTask = AppCleanerDeleteTask(
                                    setOf(it.appJunk.identifier),
                                    targetFilters = setOf(it.category),
                                    targetContents = setOf(it.lookup.lookedUp),
                                    includeInaccessible = false,
                                )
                                events.postValue(AppJunkEvents.ConfirmDeletion(junk, deleteTask))
                            },
                        )
                    }
                    .sortedByDescending { it.lookup.size }
                    .run { categoryGroup.addAll(this) }

                categoryGroup
            }
            ?.flatten()
            ?.run { items.addAll(this) }

        State(
            junk = junk,
            items = items,
            progress = progress,
        )
    }.asLiveData2()

    data class State(
        val junk: AppJunk,
        val items: List<AppJunkElementsAdapter.Item>,
        val progress: Progress.Data?,
    )

    fun doDelete(task: AppCleanerDeleteTask) = launch {
        log(TAG, INFO) { "doDelete($task)" }
        if (!upgradeRepo.isPro()) {
            MainDirections.goToUpgradeFragment().navigate()
            return@launch
        }
        // Removing the AppJunk, removes the fragment and also this viewmodel, so we can't post our own result
        events.postValue(AppJunkEvents.TaskForParent(task))
    }

    fun exclude(path: APath) = launch {
        log(TAG) { "excludeApp(): $path" }
        appCleaner.exclude(args.identifier, setOf(path))
    }

    fun exclude(selected: Collection<AppJunkElementsAdapter.Item>) = launch {
        log(TAG) { "excludeContent(): ${selected.size} items" }
        val paths = selected.mapNotNull {
            when (it) {
                is AppJunkElementFileVH.Item -> it.lookup.lookedUp
                else -> null
            }
        }.toSet()
        appCleaner.exclude(args.identifier, paths)
    }

    fun delete(selected: Collection<AppJunkElementsAdapter.Item>) = launch {
        val junk = currentAppJunk.first()

        val deleteTask = AppCleanerDeleteTask(
            setOf(args.identifier),
            targetFilters = selected.mapNotNull {
                when (it) {
                    is AppJunkElementFileVH.Item -> it.category
                    else -> null
                }
            }.toSet(),
            targetContents = selected.mapNotNull {
                when (it) {
                    is AppJunkElementFileVH.Item -> it.lookup.lookedUp
                    else -> null
                }
            }.toSet(),
            includeInaccessible = selected.any { it is AppJunkElementInaccessibleVH.Item },
        )
        events.postValue(AppJunkEvents.ConfirmDeletion(junk, deleteTask))
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Details", "Fragment", "VM")
    }
}