package eu.darken.sdmse.appcleaner.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilterIdentifier
import eu.darken.sdmse.appcleaner.core.hasData
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask
import eu.darken.sdmse.appcleaner.ui.AppJunkDetailsRoute
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.PagedDetailsViewModel
import eu.darken.sdmse.common.uix.resolveTarget
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.main.core.taskmanager.uniqueTaskResults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class AppJunkDetailsViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val appCleaner: AppCleaner,
    private val taskSubmitter: TaskSubmitter,
    private val upgradeRepo: UpgradeRepo,
) : PagedDetailsViewModel<AppJunkDetailsRoute, InstallId>(handle, dispatcherProvider, tag = TAG) {

    val events = SingleEventFlow<Event>()

    private val collapsedByJunk =
        MutableStateFlow<Map<InstallId, Set<ExpendablesFilterIdentifier>>>(emptyMap())

    init {
        // `mapNotNull { it.data }` skips the null transitions that performScan publishes at
        // the start of a refresh, so navUp fires only on real "drain to empty", not loading.
        autoNavUpOnEmpty(appCleaner.state.mapNotNull { it.data }.map { it.hasData })

        taskSubmitter.uniqueTaskResults<AppCleanerTask.Result>(SDMTool.Type.APPCLEANER)
            .onEach { events.tryEmit(Event.TaskResult(it)) }
            .launchInViewModel()
    }

    val state: StateFlow<State> = routeFlow.filterNotNull().flatMapLatest { route ->
        // Item production excludes progress so high-frequency progress ticks during a scan don't
        // re-sort the junk list and re-run resolveTarget. Progress is merged in last (below) as a
        // cheap field swap that preserves the items List instance, letting keyed pager pages skip.
        val itemsState = combine(
            appCleaner.state.map { it.data }.filterNotNull(),
            collapsedByJunk,
        ) { data, collapsed ->
            // Drop fully-empty junks left over after path-only `appCleaner.exclude(installId, paths)`
            // calls — backend doesn't filter them out, so without this the pager would show ghost
            // zero-junk pages.
            val visible = data.junks
                .filter { !it.isEmpty() }
                .sortedByDescending { it.size }

            val availableTarget = resolveTarget(
                items = visible,
                requestedTarget = currentTarget ?: route.identifier,
                lastPosition = lastPosition,
                identifierOf = { it.identifier },
                onPositionTracked = { lastPosition = it },
            )

            State(
                items = visible,
                target = availableTarget,
                collapsedByJunk = collapsed,
            )
        }

        combine(itemsState, appCleaner.progress) { base, progress ->
            base.copy(progress = progress)
        }
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    fun onPageChanged(identifier: InstallId) {
        log(TAG) { "onPageChanged($identifier)" }
        currentTarget = identifier
    }

    fun onToggleCategoryCollapse(installId: InstallId, category: ExpendablesFilterIdentifier) {
        collapsedByJunk.update { current ->
            val existing = current[installId].orEmpty()
            val next = if (category in existing) existing - category else existing + category
            current + (installId to next)
        }
    }

    fun requestDelete(spec: DeleteSpec) = launch {
        log(TAG, INFO) { "requestDelete($spec)" }
        if (!upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        val junk = currentJunk(spec.installId) ?: return@launch
        if (!isSpecExecutable(spec, junk)) return@launch
        events.tryEmit(Event.ConfirmDelete(spec))
    }

    fun confirmDelete(spec: DeleteSpec) = launch {
        log(TAG, INFO) { "confirmDelete($spec)" }
        if (!upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        val junk = currentJunk(spec.installId) ?: return@launch
        val task = buildAppCleanerTask(spec, junk) ?: return@launch
        taskSubmitter.submit(task)
    }

    fun onExcludeJunk(installId: InstallId) = launch {
        log(TAG, INFO) { "onExcludeJunk($installId)" }
        val junk = currentJunk(installId) ?: return@launch
        val undo = appCleaner.exclude(setOf(junk.identifier))
        events.tryEmit(
            Event.HeaderExclusionCreated(
                count = undo.exclusionIds.size,
                undo = undo,
                restoreTarget = installId,
            ),
        )
    }

    fun onExcludeSelectedFiles(installId: InstallId, paths: Set<APath>) = launch {
        log(TAG, INFO) { "onExcludeSelectedFiles($installId, ${paths.size})" }
        if (paths.isEmpty()) return@launch
        val junk = currentJunk(installId) ?: return@launch
        val livePaths = junk.expendables?.values?.flatten()?.map { it.path }?.toSet().orEmpty()
        val validPaths = paths intersect livePaths
        if (validPaths.isEmpty()) return@launch
        appCleaner.exclude(junk.identifier, validPaths)
        events.tryEmit(Event.SelectionExclusionsCreated(validPaths.size))
    }

    fun onUndoExclude(undo: AppCleaner.ExclusionUndo, restoreTarget: InstallId) = launch {
        log(TAG, INFO) { "onUndoExclude(${undo.exclusionIds.size}, restore=$restoreTarget)" }
        currentTarget = restoreTarget
        appCleaner.undoExclude(undo)
    }

    fun onShowExclusions() {
        navTo(ExclusionsListRoute)
    }

    private suspend fun currentJunk(installId: InstallId): AppJunk? {
        val data = appCleaner.state.first().data ?: return null
        return data.junks.firstOrNull { it.identifier == installId }
    }

    private fun isSpecExecutable(spec: DeleteSpec, junk: AppJunk): Boolean = when (spec) {
        is DeleteSpec.WholeJunk -> !junk.isEmpty()
        is DeleteSpec.Inaccessible -> junk.inaccessibleCache != null
        is DeleteSpec.Category -> junk.expendables?.get(spec.category)?.isNotEmpty() == true
        is DeleteSpec.SingleFile ->
            junk.expendables?.get(spec.category)?.any { it.path == spec.path } == true
        is DeleteSpec.SelectedFiles -> {
            val livePaths = junk.expendables?.values?.flatten()?.map { it.path }?.toSet().orEmpty()
            (spec.paths intersect livePaths).isNotEmpty()
        }
    }

    data class State(
        val items: List<AppJunk> = emptyList(),
        val target: InstallId? = null,
        val progress: Progress.Data? = null,
        val collapsedByJunk: Map<InstallId, Set<ExpendablesFilterIdentifier>> = emptyMap(),
    )

    sealed interface Event {
        data class TaskResult(val result: AppCleanerTask.Result) : Event
        data class ConfirmDelete(val spec: DeleteSpec) : Event
        data class HeaderExclusionCreated(
            val count: Int,
            val undo: AppCleaner.ExclusionUndo,
            val restoreTarget: InstallId,
        ) : Event
        data class SelectionExclusionsCreated(val count: Int) : Event
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Details", "ViewModel")
    }
}
