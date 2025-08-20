package eu.darken.sdmse.appcontrol.ui.list.actions

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.createGooglePlayIntent
import eu.darken.sdmse.appcontrol.core.createSystemSettingsIntent
import eu.darken.sdmse.appcontrol.core.export.AppExportTask
import eu.darken.sdmse.appcontrol.core.forcestop.ForceStopTask
import eu.darken.sdmse.appcontrol.core.toggle.AppControlToggleTask
import eu.darken.sdmse.appcontrol.core.uninstall.UninstallException
import eu.darken.sdmse.appcontrol.core.uninstall.UninstallTask
import eu.darken.sdmse.appcontrol.ui.list.actions.items.AppStoreActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.ExcludeActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.ExportActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.ForceStopActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.InfoSizeVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.InfoUsageVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.LaunchActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.SystemSettingsActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.ToggleActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.UninstallActionVH
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.getLaunchIntent
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.current
import eu.darken.sdmse.exclusion.core.save
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject


@HiltViewModel
class AppActionViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @param:ApplicationContext private val context: Context,
    appControl: AppControl,
    private val taskManager: TaskManager,
    private val exclusionManager: ExclusionManager,
    private val userManager2: UserManager2,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<AppActionDialogArgs>()
    private val installId: InstallId = navArgs.installId

    init {
        appControl.state
            .map { state -> state.data?.apps?.singleOrNull { it.installId == installId } }
            .filter { it == null }
            .take(1)
            .onEach {
                log(TAG) { "App data for $installId is no longer available" }
                popNavStack()
            }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<AppActionEvents>()

    val state = combineTransform(
        appControl.state.mapNotNull { state -> state.data?.apps?.singleOrNull { it.installId == installId } },
        appControl.state,
        exclusionManager.exclusions,
        appControl.progress,
    ) { appInfo, state, _, progress ->
        val baseState = State(
            appInfo = appInfo,
            progress = progress ?: Progress.Data(),
        )
        emit(baseState)

        val isCurrentUser = appInfo.installId.userHandle == userManager2.currentUser().handle

        val sizeAction = appInfo.sizes?.let {
            InfoSizeVH.Item(
                appInfo,
                onClicked = {
                    // TODO nicer target than this?
                    val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        errorEvents.postValue(e)
                    }
                }
            )
        }

        val usageAction = InfoUsageVH.Item(
            appInfo,
            onClicked = {
                try {
                    try {
                        val wellBeing = Intent().apply {
                            component = ComponentName(
                                "com.google.android.apps.wellbeing",
                                "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity"
                            )
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(wellBeing)
                    } catch (e: ActivityNotFoundException) {
                        val fallback = appInfo.createSystemSettingsIntent(context)
                        context.startActivity(fallback)
                    }
                } catch (e: Exception) {
                    errorEvents.postValue(e)
                }
            }
        )

        val launchAction = appInfo.pkg.id.getLaunchIntent(context)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?.takeIf { isCurrentUser }
            ?.let { intent ->
                LaunchActionVH.Item(
                    appInfo = appInfo,
                    onLaunch = {
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            errorEvents.postValue(e)
                        } catch (e: SecurityException) {
                            errorEvents.postValue(e)
                        }
                    }
                )
            }

        val forceStopAction = if (state.canForceStop && appInfo.canBeStopped) {
            ForceStopActionVH.Item(
                appInfo = appInfo,
                onForceStop = {
                    launch {
                        val result = taskManager.submit(ForceStopTask(setOf(appInfo.installId))) as ForceStopTask.Result
                        events.postValue(AppActionEvents.ShowResult(result))
                    }
                }
            )
        } else {
            null
        }

        val systemSettingsAction = if (isCurrentUser) {
            SystemSettingsActionVH.Item(
                appInfo = appInfo,
                onSettings = {
                    val intent = it.createSystemSettingsIntent(context)
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        log(TAG, ERROR) { "Launching system settings intent failed: ${e.asLog()}" }
                        errorEvents.postValue(e)
                    }
                }
            )
        } else {
            null
        }

        val existingExclusion = exclusionManager
            .current()
            .filterIsInstance<Exclusion.Pkg>()
            .firstOrNull { it.match(appInfo.id) }

        val excludeAction = if (isCurrentUser) {
            ExcludeActionVH.Item(
                appInfo = appInfo,
                exclusion = existingExclusion,
                onExclude = {
                    launch {
                        if (existingExclusion != null) {
                            MainDirections.goToPkgExclusionEditor(
                                exclusionId = existingExclusion.id,
                                initial = null,
                            ).navigate()
                        } else {
                            val newExcl = PkgExclusion(pkgId = appInfo.id)
                            exclusionManager.save(newExcl)
                        }
                    }
                },
            )
        } else {
            null
        }

        val appStoreAction = (appInfo.pkg as? InstallDetails)
            ?.takeIf { it.installerInfo.installer != null }
            ?.takeIf { isCurrentUser }
            ?.let {
                AppStoreActionVH.Item(
                    appInfo = appInfo,
                    onAppStore = { info ->
                        val intent = info.createGooglePlayIntent(context)
                        context.startActivity(intent)
                    }
                )
            }

        val uninstallAction = if (appInfo.canBeDeleted) {
            UninstallActionVH.Item(
                appInfo = appInfo,
                onItemClicked = { info ->
                    val task = UninstallTask(setOf(info.installId))
                    launch {
                        val result = taskManager.submit(task) as UninstallTask.Result
                        if (result.failed.isNotEmpty()) throw UninstallException(installId = result.failed.first())
                        else events.postValue(AppActionEvents.ShowResult(result))
                    }
                }
            )
        } else {
            null
        }

        val disableAction = if (state.canToggle && appInfo.canBeToggled) {
            ToggleActionVH.Item(
                appInfo = appInfo,
                onToggle = {
                    val task = AppControlToggleTask(setOf(appInfo.installId))
                    launch {
                        val result = taskManager.submit(task) as AppControlToggleTask.Result
                        events.postValue(AppActionEvents.ShowResult(result))
                    }
                }
            )
        } else {
            null
        }

        val exportaction = if (appInfo.canBeExported && isCurrentUser) {
            ExportActionVH.Item(
                appInfo = appInfo,
                onBackup = {
                    events.postValue(AppActionEvents.SelectExportPath(it, Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)))
                }
            )
        } else {
            null
        }

        val finalState = baseState.copy(
            actions = listOfNotNull(
                sizeAction,
                usageAction,
                launchAction,
                forceStopAction,
                systemSettingsAction,
                appStoreAction,
                excludeAction,
                disableAction,
                uninstallAction,
                exportaction,
            ),
            progress = progress,
        )
        emit(finalState)
    }
        .asLiveData2()

    fun exportApp(saveDir: Uri?) = launch {
        if (saveDir == null) {
            log(TAG, WARN) { "Export failed, no path picked" }
            return@launch
        }
        log(TAG) { "exportApp($saveDir)" }

        val result = taskManager.submit(
            AppExportTask(
                targets = setOf(state.value!!.appInfo.installId),
                savePath = saveDir,
            )
        ) as AppExportTask.Result

        events.postValue(AppActionEvents.ShowResult(result))
    }

    data class State(
        val appInfo: AppInfo,
        val progress: Progress.Data?,
        val actions: List<AppActionAdapter.Item>? = null,
    )

    companion object {
        private val TAG = logTag("AppControl", "Action", "Dialog", "ViewModel")
    }
}