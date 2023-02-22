package eu.darken.sdmse.appcleaner.ui.details.appjunk

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
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
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.reflect.KClass

@HiltViewModel
class AppJunkFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val appCleaner: AppCleaner,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args = AppJunkFragmentArgs.fromSavedStateHandle(handle)

    val events = SingleLiveEvent<AppJunkEvents>()

    val state = combine(
        appCleaner.data
            .filterNotNull()
            .map { data -> data.junks.singleOrNull { it.identifier == args.identifier } }
            .filterNotNull(),
        appCleaner.progress,
    ) { data, progress ->
        val items = mutableListOf<AppJunkElementsAdapter.Item>()

        AppJunkElementHeaderVH.Item(
            appJunk = data,
            onDeleteAllClicked = { events.postValue(AppJunkEvents.ConfirmDeletion(it.appJunk)) },
            onExcludeClicked = {
//                    TODO()
            }
        ).run { items.add(this) }

        data.inaccessibleCache?.let {
            AppJunkElementInaccessibleVH.Item(
                appJunk = data,
                inaccessibleCache = data.inaccessibleCache,
                onItemClick = {
                    events.postValue(AppJunkEvents.ConfirmDeletion(it.appJunk, onlyInaccessible = true))
                }
            ).run { items.add(this) }
        }

        data.expendables
            ?.filter { it.value.isNotEmpty() }
            ?.map { (category, paths) ->
                val categoryGroup = mutableListOf<AppJunkElementsAdapter.Item>()

                AppJunkElementFileCategoryVH.Item(
                    appJunk = data,
                    category = category,
                    paths = paths,
                    onItemClick = {
                        events.postValue(AppJunkEvents.ConfirmDeletion(it.appJunk, it.category))
                    }
                ).run { categoryGroup.add(this) }

                paths
                    .map { lookup ->
                        AppJunkElementFileVH.Item(
                            appJunk = data,
                            category = category,
                            lookup = lookup,
                            onItemClick = {
                                events.postValue(AppJunkEvents.ConfirmDeletion(it.appJunk, it.category, it.lookup))
                            }
                        )
                    }
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

    fun doDelete(
        appJunk: AppJunk,
        filterTypes: Set<KClass<out ExpendablesFilter>>?,
        paths: Set<APath>?,
        onlyInaccessible: Boolean,
    ) = launch {
        log(TAG, INFO) { "doDelete(appJunk=$appJunk, filterTypes=$filterTypes,paths=$paths)" }
        val task = AppCleanerDeleteTask(targetPkgs = setOf(appJunk.pkg.id), filterTypes, paths, onlyInaccessible)
        // Removnig the AppJunk, removes the fragment and also this viewmodel, so we can't post our own result
        events.postValue(AppJunkEvents.TaskForParent(task))
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Details", "Fragment", "VM")
    }
}