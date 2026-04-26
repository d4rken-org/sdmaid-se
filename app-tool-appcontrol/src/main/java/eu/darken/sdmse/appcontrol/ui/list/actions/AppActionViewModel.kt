package eu.darken.sdmse.appcontrol.ui.list.actions

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.archive.ArchiveException
import eu.darken.sdmse.appcontrol.core.archive.ArchiveTask
import eu.darken.sdmse.appcontrol.core.createGooglePlayIntent
import eu.darken.sdmse.appcontrol.core.createSystemSettingsIntent
import eu.darken.sdmse.appcontrol.core.export.AppExportTask
import eu.darken.sdmse.appcontrol.core.forcestop.ForceStopTask
import eu.darken.sdmse.appcontrol.core.restore.RestoreException
import eu.darken.sdmse.appcontrol.core.restore.RestoreTask
import eu.darken.sdmse.appcontrol.core.toggle.AppControlToggleTask
import eu.darken.sdmse.appcontrol.core.uninstall.UninstallException
import eu.darken.sdmse.appcontrol.core.uninstall.UninstallTask
import eu.darken.sdmse.appcontrol.ui.list.actions.items.AppActionItem
import eu.darken.sdmse.appcontrol.ui.list.actions.items.AppActionItemContext
import eu.darken.sdmse.appcontrol.ui.list.actions.items.buildAppActionItems
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.getLaunchIntent
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.current
import eu.darken.sdmse.exclusion.core.save
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.exclusion.ui.PkgExclusionEditorRoute
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class AppActionViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val appControl: AppControl,
    private val taskManager: TaskSubmitter,
    private val exclusionManager: ExclusionManager,
    private val userManager2: UserManager2,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    // Bound by the host via [setInstallId] (Picker-style entry-arg binding).
    // SavedStateHandle.toRoute<>() is unreliable for non-nullable serializable args under Nav3 —
    // see project memory `feedback_nav3_route_binding`.
    private val installIdFlow = MutableStateFlow<InstallId?>(null)

    fun setInstallId(installId: InstallId) {
        if (installIdFlow.value == installId) return
        installIdFlow.value = installId
    }

    init {
        installIdFlow.filterNotNull()
            .flatMapLatest { id ->
                appControl.state
                    .map { it.data?.apps?.firstOrNull { app -> app.installId == id } to id }
            }
            .filter { (app, _) -> app == null }
            .take(1)
            .onEach { (_, id) ->
                log(TAG) { "App data for $id is no longer available, dismissing sheet" }
                navUp()
            }
            .launchIn(vmScope)
    }

    val events = SingleEventFlow<Event>()

    val state: StateFlow<State> = combine(
        installIdFlow,
        appControl.state,
        appControl.progress,
        exclusionManager.exclusions,
    ) { installId, acState, progress, _ ->
        if (installId == null) return@combine State()
        val appInfo = acState.data?.apps?.firstOrNull { it.installId == installId }
            ?: return@combine State()

        val isCurrentUser = appInfo.installId.userHandle == userManager2.currentUser().handle
        val launchAvailable = appInfo.pkg.id.getLaunchIntent(context) != null
        val appStoreAvailable = (appInfo.pkg as? InstallDetails)?.installerInfo?.installer != null
        val existingExclusion = exclusionManager
            .current()
            .filterIsInstance<Exclusion.Pkg>()
            .firstOrNull { it.match(appInfo.id) }

        val ctx = AppActionItemContext(
            isCurrentUser = isCurrentUser,
            launchAvailable = launchAvailable,
            appStoreAvailable = appStoreAvailable,
            canForceStop = acState.canForceStop,
            canArchive = acState.canArchive,
            canRestore = acState.canRestore,
            canToggle = acState.canToggle,
            existingExclusionId = existingExclusion?.id,
        )
        val items = buildAppActionItems(appInfo, ctx)
        State(appInfo = appInfo, progress = progress, items = items)
    }.safeStateIn(
        initialValue = State(),
        onError = { State() },
    )

    private suspend fun currentAppInfoOrNull(): AppInfo? {
        val id = installIdFlow.value ?: return null
        return appControl.state.first().data?.apps?.firstOrNull { it.installId == id }
    }

    fun onActionTapped(item: AppActionItem) = launch {
        log(TAG, INFO) { "onActionTapped($item)" }
        val id = installIdFlow.value ?: return@launch
        when (item) {
            is AppActionItem.Info.Size -> openStorageSettings()
            is AppActionItem.Info.Usage -> openUsageScreen()
            is AppActionItem.Action.Launch -> launchApp()
            is AppActionItem.Action.SystemSettings -> openSystemSettings()
            is AppActionItem.Action.AppStore -> openAppStore()
            is AppActionItem.Action.ForceStop -> submitForceStop()
            is AppActionItem.Action.Toggle -> submitToggle()
            is AppActionItem.Action.Exclude -> handleExclude(item)
            is AppActionItem.Action.Uninstall -> events.emit(Event.ConfirmUninstall(id))
            is AppActionItem.Action.Archive -> events.emit(Event.ConfirmArchive(id))
            is AppActionItem.Action.Restore -> events.emit(Event.ConfirmRestore(id))
            is AppActionItem.Action.Export -> requestExportPath()
        }
    }

    fun onUninstallConfirmed() = launch {
        val appInfo = currentAppInfoOrNull() ?: return@launch
        log(TAG, INFO) { "onUninstallConfirmed(${appInfo.installId})" }
        val task = UninstallTask(setOf(appInfo.installId))
        val result = taskManager.submit(task) as UninstallTask.Result
        if (result.failed.isNotEmpty()) {
            throw UninstallException(installId = result.failed.first())
        }
        events.emit(Event.ShowResult(result))
    }

    fun onArchiveConfirmed() = launch {
        val appInfo = currentAppInfoOrNull() ?: return@launch
        log(TAG, INFO) { "onArchiveConfirmed(${appInfo.installId})" }
        val task = ArchiveTask(setOf(appInfo.installId))
        val result = taskManager.submit(task) as ArchiveTask.Result
        if (result.failed.isNotEmpty()) {
            throw ArchiveException(installId = result.failed.first())
        }
        events.emit(Event.ShowResult(result))
    }

    fun onRestoreConfirmed() = launch {
        val appInfo = currentAppInfoOrNull() ?: return@launch
        log(TAG, INFO) { "onRestoreConfirmed(${appInfo.installId})" }
        val task = RestoreTask(setOf(appInfo.installId))
        val result = taskManager.submit(task) as RestoreTask.Result
        if (result.failed.isNotEmpty()) {
            throw RestoreException(installId = result.failed.first())
        }
        events.emit(Event.ShowResult(result))
    }

    fun onExportPathPicked(uri: Uri?) = launch {
        if (uri == null) {
            log(TAG, WARN) { "Export failed, no path picked" }
            return@launch
        }
        val appInfo = currentAppInfoOrNull() ?: return@launch
        log(TAG, INFO) { "onExportPathPicked($uri)" }
        val task = AppExportTask(targets = setOf(appInfo.installId), savePath = uri)
        val result = taskManager.submit(task) as AppExportTask.Result
        events.emit(Event.ShowResult(result))
    }

    private suspend fun submitToggle() {
        val appInfo = currentAppInfoOrNull() ?: return
        val task = AppControlToggleTask(setOf(appInfo.installId))
        val result = taskManager.submit(task) as AppControlToggleTask.Result
        events.emit(Event.ShowResult(result))
    }

    private suspend fun submitForceStop() {
        val appInfo = currentAppInfoOrNull() ?: return
        val task = ForceStopTask(setOf(appInfo.installId))
        val result = taskManager.submit(task) as ForceStopTask.Result
        events.emit(Event.ShowResult(result))
    }

    private suspend fun handleExclude(item: AppActionItem.Action.Exclude) {
        if (item.existingExclusionId != null) {
            navTo(PkgExclusionEditorRoute(exclusionId = item.existingExclusionId))
            return
        }
        val appInfo = currentAppInfoOrNull() ?: return
        val newExcl = PkgExclusion(pkgId = appInfo.id)
        exclusionManager.save(newExcl)
    }

    private suspend fun launchApp() {
        val appInfo = currentAppInfoOrNull() ?: return
        val intent = appInfo.pkg.id.getLaunchIntent(context)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?: return
        runCatching { context.startActivity(intent) }
            .onFailure { log(TAG, WARN) { "Launch failed: ${it.asLog()}" }; errorEvents.emit(it) }
    }

    private suspend fun openSystemSettings() {
        val appInfo = currentAppInfoOrNull() ?: return
        val intent = appInfo.createSystemSettingsIntent(context)
        runCatching { context.startActivity(intent) }
            .onFailure { log(TAG, ERROR) { "System settings intent failed: ${it.asLog()}" }; errorEvents.emit(it) }
    }

    private suspend fun openAppStore() {
        val appInfo = currentAppInfoOrNull() ?: return
        val intent = appInfo.createGooglePlayIntent(context)
        runCatching { context.startActivity(intent) }
            .onFailure { log(TAG, WARN) { "AppStore intent failed: ${it.asLog()}" }; errorEvents.emit(it) }
    }

    private fun openStorageSettings() {
        val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { context.startActivity(intent) }
            .onFailure { errorEvents.emitBlocking(it) }
    }

    private fun openUsageScreen() {
        val wellBeing = Intent().apply {
            component = ComponentName(
                "com.google.android.apps.wellbeing",
                "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity",
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(wellBeing)
        } catch (_: android.content.ActivityNotFoundException) {
            // Wellbeing not installed; fall back to per-app system settings.
            launch {
                val appInfo = currentAppInfoOrNull() ?: return@launch
                val fallback = appInfo.createSystemSettingsIntent(context)
                runCatching { context.startActivity(fallback) }
                    .onFailure { errorEvents.emit(it) }
            }
        } catch (e: Exception) {
            errorEvents.emitBlocking(e)
        }
    }

    private fun requestExportPath() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        events.tryEmit(Event.SelectExportPath(intent))
    }

    data class State(
        val appInfo: AppInfo? = null,
        val progress: Progress.Data? = null,
        val items: List<AppActionItem>? = null,
    )

    sealed interface Event {
        data class ShowResult(val result: eu.darken.sdmse.appcontrol.core.AppControlTask.Result) : Event
        data class SelectExportPath(val intent: Intent) : Event
        data class ConfirmUninstall(val installId: InstallId) : Event
        data class ConfirmArchive(val installId: InstallId) : Event
        data class ConfirmRestore(val installId: InstallId) : Event
    }

    companion object {
        private val TAG = logTag("AppControl", "Action", "ViewModel")
    }
}
