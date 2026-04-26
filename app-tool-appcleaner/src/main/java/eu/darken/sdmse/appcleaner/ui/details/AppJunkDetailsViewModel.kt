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
import eu.darken.sdmse.common.navigation.mutableState
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.uix.resolveTarget
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.main.core.taskmanager.getLatestTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class AppJunkDetailsViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val appCleaner: AppCleaner,
    private val taskSubmitter: TaskSubmitter,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private val initialIdentifier: InstallId? = AppJunkDetailsRoute.from(handle).identifier
    private var currentTarget: InstallId? by handle.mutableState("target")
    private var lastPosition: Int? by handle.mutableState("position")

    val events = SingleEventFlow<Event>()

    private val collapsedByJunk =
        MutableStateFlow<Map<InstallId, Set<ExpendablesFilterIdentifier>>>(emptyMap())

    init {
        appCleaner.state
            .map { it.data }
            .filter { !it.hasData }
            .take(1)
            .onEach { navUp() }
            .launchIn(vmScope)

        val start = Instant.now()
        val handledResults = mutableSetOf<String>()
        taskSubmitter.state
            .map { it.getLatestTask(SDMTool.Type.APPCLEANER) }
            .filterNotNull()
            .filter { it.completedAt!! > start }
            .onEach { task ->
                val result = task.result as? AppCleanerTask.Result ?: return@onEach
                if (!handledResults.contains(task.id)) {
                    handledResults.add(task.id)
                    events.tryEmit(Event.TaskResult(result))
                }
            }
            .launchIn(vmScope)
    }

    val state: StateFlow<State> = combine(
        appCleaner.progress,
        appCleaner.state.map { it.data }.filterNotNull(),
        collapsedByJunk,
    ) { progress, data, collapsed ->
        // Drop fully-empty junks left over after path-only `appCleaner.exclude(installId, paths)`
        // calls — backend doesn't filter them out, so without this the pager would show ghost
        // zero-junk pages.
        val visible = data.junks
            .filter { !it.isEmpty() }
            .sortedByDescending { it.size }

        val availableTarget = resolveTarget(
            items = visible,
            requestedTarget = currentTarget ?: initialIdentifier,
            lastPosition = lastPosition,
            identifierOf = { it.identifier },
            onPositionTracked = { lastPosition = it },
        )

        State(
            items = visible,
            target = availableTarget,
            progress = progress,
            collapsedByJunk = collapsed,
        )
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
        appCleaner.exclude(setOf(junk.identifier))
        events.tryEmit(Event.ExclusionsCreated(1))
    }

    fun onExcludeSelectedFiles(installId: InstallId, paths: Set<APath>) = launch {
        log(TAG, INFO) { "onExcludeSelectedFiles($installId, ${paths.size})" }
        if (paths.isEmpty()) return@launch
        val junk = currentJunk(installId) ?: return@launch
        val livePaths = junk.expendables?.values?.flatten()?.map { it.path }?.toSet().orEmpty()
        val validPaths = paths intersect livePaths
        if (validPaths.isEmpty()) return@launch
        appCleaner.exclude(junk.identifier, validPaths)
        events.tryEmit(Event.ExclusionsCreated(validPaths.size))
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
        data class ExclusionsCreated(val count: Int) : Event
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Details", "ViewModel")
    }
}
