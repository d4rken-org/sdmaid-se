package eu.darken.sdmse.appcontrol.ui.list.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.MainDirections
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.createGooglePlayIntent
import eu.darken.sdmse.appcontrol.core.createSystemSettingsIntent
import eu.darken.sdmse.appcontrol.core.toggle.AppControlToggleTask
import eu.darken.sdmse.appcontrol.core.uninstall.UninstallException
import eu.darken.sdmse.appcontrol.core.uninstall.UninstallTask
import eu.darken.sdmse.appcontrol.ui.list.actions.items.AppStoreActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.ExcludeActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.LaunchActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.SystemSettingsActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.ToggleActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.UninstallActionVH
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.ExtendedInstallData
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.currentExclusions
import eu.darken.sdmse.exclusion.core.save
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.combine
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
    @ApplicationContext private val context: Context,
    private val appControl: AppControl,
    private val taskManager: TaskManager,
    private val exclusionManager: ExclusionManager,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<AppActionDialogArgs>()
    private val pkgId: Pkg.Id = navArgs.pkgId

    init {
        appControl.state
            .map { state -> state.data?.apps?.singleOrNull { it.pkg.id == pkgId } }
            .filter { it == null }
            .take(1)
            .onEach {
                log(TAG) { "App data for $pkgId is no longer available" }
                popNavStack()
            }
            .launchInViewModel()
    }

    val events = SingleLiveEvent<AppActionEvents>()

    val state = combine(
        exclusionManager.exclusions,
        appControl.state,
        appControl.state.mapNotNull { state -> state.data?.apps?.singleOrNull { it.pkg.id == pkgId } },
        appControl.progress,
    ) { exclusions, state, appInfo, progress ->
        val launchAction = context.packageManager
            .getLaunchIntentForPackage(appInfo.pkg.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
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

        val systemSettingsAction = SystemSettingsActionVH.Item(
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

        val existingExclusion = exclusionManager
            .currentExclusions()
            .filterIsInstance<Exclusion.Pkg>()
            .firstOrNull { it.match(appInfo.id) }

        val excludeAction = ExcludeActionVH.Item(
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

        val appStoreAction = (appInfo.pkg as? ExtendedInstallData)
            ?.takeIf { it.installerInfo.installer != null }
            ?.let {
                AppStoreActionVH.Item(
                    appInfo = appInfo,
                    onAppStore = { info ->
                        val intent = info.createGooglePlayIntent(context)
                        context.startActivity(intent)
                    }
                )
            }

        val uninstallAction = UninstallActionVH.Item(
            appInfo = appInfo,
            onItemClicked = { info ->
                launch {
                    val result = appControl.submit(UninstallTask(setOf(info.installId))) as UninstallTask.Result
                    if (result.failed.isNotEmpty()) {
                        throw UninstallException(result.failed.first())
                    }
                }
            }
        )

        val disableAction = if (state.isAppToggleAvailable) {
            ToggleActionVH.Item(
                appInfo = appInfo,
                onToggle = {
                    val task = AppControlToggleTask(setOf(appInfo.installId))
                    launch { taskManager.submit(task) }
                }
            )
        } else {
            null
        }

        State(
            progress = progress,
            appInfo = appInfo,
            actions = listOfNotNull(
                launchAction,
                systemSettingsAction,
                appStoreAction,
                excludeAction,
                disableAction,
                uninstallAction,
            ).filterNotNull()
        )
    }
        .asLiveData2()

    data class State(
        val progress: Progress.Data?,
        val appInfo: AppInfo,
        val actions: List<AppActionAdapter.Item>?,
    )

    companion object {
        private val TAG = logTag("AppControl", "Action", "Dialog", "ViewModel")
    }

}