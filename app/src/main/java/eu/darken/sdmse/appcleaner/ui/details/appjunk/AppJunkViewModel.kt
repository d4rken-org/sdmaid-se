package eu.darken.sdmse.appcleaner.ui.details.appjunk

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementFileCategoryVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementFileVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementHeaderVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementInaccessibleVH
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.reflect.KClass

@HiltViewModel
class AppJunkViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val appCleaner: AppCleaner,
    private val upgradeRepo: UpgradeRepo,
    private val taskManager: TaskManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args = AppJunkFragmentArgs.fromSavedStateHandle(handle)

    val events = SingleLiveEvent<AppJunkEvents>()

    private val collapsedCategories = MutableStateFlow<Set<KClass<out ExpendablesFilter>>>(emptySet())

    private val currentAppJunk = appCleaner.state
        .map { it.data }
        .filterNotNull()
        .map { data -> data.junks.singleOrNull { it.identifier == args.identifier } }
        .filterNotNull()

    val state = combine(currentAppJunk, appCleaner.progress, collapsedCategories) { junk, progress, collapsed ->
        val items = mutableListOf<AppJunkElementsAdapter.Item>()

        AppJunkElementHeaderVH.Item(
            appJunk = junk,
            onDeleteAllClicked = { delete(setOf(it)) },
            onExcludeClicked = { exclude(setOf(it)) },
        ).run { items.add(this) }

        junk.inaccessibleCache?.let {
            AppJunkElementInaccessibleVH.Item(
                appJunk = junk,
                inaccessibleCache = junk.inaccessibleCache,
                onItemClick = { delete(setOf(it)) }
            ).run { items.add(this) }
        }

        junk.expendables
            ?.filter { it.value.isNotEmpty() }
            ?.map { (category, matches) ->
                val categoryGroup = mutableListOf<AppJunkElementsAdapter.Item>()
                val isCollapsed = collapsed.contains(category)

                AppJunkElementFileCategoryVH.Item(
                    appJunk = junk,
                    category = category,
                    matches = matches,
                    onItemClick = { delete(setOf(it)) },
                    isCollapsed = isCollapsed,
                    onCollapseToggle = { toggleCategoryCollapse(category) },
                ).run { categoryGroup.add(this) }

                if (!isCollapsed) {
                    matches
                        .map { match ->
                            AppJunkElementFileVH.Item(
                                appJunk = junk,
                                category = category,
                                match = match,
                                onItemClick = { delete(setOf(it)) },
                            )
                        }
                        .sortedByDescending { it.match.expectedGain }
                        .run { categoryGroup.addAll(this) }
                }

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

    fun exclude(selected: Collection<AppJunkElementsAdapter.Item>) = launch {
        log(TAG) { "excludeContent(): ${selected.size} items" }
        val junk = currentAppJunk.first()

        val paths = selected.mapNotNull {
            when (it) {
                is AppJunkElementFileVH.Item -> it.match.path
                else -> null
            }
        }.toSet()

        if (paths.isEmpty()) {
            appCleaner.exclude(setOf(junk.identifier))
        } else {
            appCleaner.exclude(junk.identifier, paths)
        }
    }

    fun delete(items: Collection<AppJunkElementsAdapter.Item>, confirmed: Boolean = false) = launch {
        if (!upgradeRepo.isPro()) {
            MainDirections.goToUpgradeFragment().navigate()
            return@launch
        }

        if (!confirmed) {
            events.postValue(AppJunkEvents.ConfirmDeletion(items))
            return@launch
        }

        val junk = currentAppJunk.first()

        val deleteTask = AppCleanerProcessingTask(
            setOf(junk.identifier),
            targetFilters = items.mapNotNull {
                when (it) {
                    is AppJunkElementFileCategoryVH.Item -> it.category
                    is AppJunkElementFileVH.Item -> it.category
                    else -> null
                }
            }.takeIf { it.isNotEmpty() }?.toSet(),
            targetContents = items.mapNotNull { item ->
                when (item) {
                    is AppJunkElementFileCategoryVH.Item -> item.matches.map { it.path }
                    is AppJunkElementFileVH.Item -> listOf(item.match.path)
                    else -> null
                }
            }.flatten().takeIf { it.isNotEmpty() }?.toSet(),
            includeInaccessible = items.any { it is AppJunkElementInaccessibleVH.Item || it is AppJunkElementHeaderVH.Item },
            onlyInaccessible = items.singleOrNull() is AppJunkElementInaccessibleVH.Item,
        )

        taskManager.submit(deleteTask)
    }

    fun toggleCategoryCollapse(category: KClass<out ExpendablesFilter>) = launch {
        val current = collapsedCategories.value
        if (current.contains(category)) {
            collapsedCategories.value = current - category
        } else {
            collapsedCategories.value = current + category
        }
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Details", "ViewModel")
    }
}