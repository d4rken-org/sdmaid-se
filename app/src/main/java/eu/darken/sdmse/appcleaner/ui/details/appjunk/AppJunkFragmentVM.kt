package eu.darken.sdmse.appcleaner.ui.details.appjunk

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerDeleteTask
import eu.darken.sdmse.appcleaner.ui.details.AppJunkDetailsFragmentDirections
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
import eu.darken.sdmse.common.pkgs.features.Installed
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

    val state = combine(
        appCleaner.data
            .filterNotNull()
            .map { data -> data.junks.singleOrNull { it.identifier == args.identifier } }
            .filterNotNull(),
        appCleaner.progress,
    ) { junk, progress ->
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
            items = items,
            progress = progress,
        )
    }.asLiveData2()

    data class State(
        val items: List<AppJunkElementsAdapter.Item>,
        val progress: Progress.Data?,
    )

    fun doDelete(task: AppCleanerDeleteTask) = launch {
        log(TAG, INFO) { "doDelete($task)" }
        if (!upgradeRepo.isPro()) {
            AppJunkDetailsFragmentDirections.actionAppCleanerDetailsFragmentToUpgradeFragment().navigate()
            return@launch
        }
        // Removnig the AppJunk, removes the fragment and also this viewmodel, so we can't post our own result
        events.postValue(AppJunkEvents.TaskForParent(task))
    }

    fun doExclude(installId: Installed.InstallId, path: APath) = launch {
        log(TAG) { "exclude(): $installId, $path" }
        appCleaner.exclude(installId, path)
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Details", "Fragment", "VM")
    }
}