package eu.darken.sdmse.appcontrol.ui.list.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcontrol.core.*
import eu.darken.sdmse.appcontrol.core.tasks.AppControlToggleTask
import eu.darken.sdmse.appcontrol.ui.list.actions.items.*
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.ExtendedInstallData
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.currentExclusions
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PackageExclusion
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.flow.*
import javax.inject.Inject


@HiltViewModel
class AppActionDialogVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val appControl: AppControl,
    private val taskManager: TaskManager,
    private val rootManager: RootManager,
    private val exclusionManager: ExclusionManager,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<AppActionDialogArgs>()
    private val pkgId: Pkg.Id = navArgs.pkgId

    init {
        appControl.data
            .map { data -> data?.apps?.singleOrNull { it.pkg.id == pkgId } }
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
        appControl.data.mapNotNull { data -> data?.apps?.singleOrNull { it.pkg.id == pkgId } },
        appControl.progress,
    ) { exclusions, appInfo, progress ->
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
                context.startActivity(intent)
            }
        )

        val uninstallAction = UninstallActionVH.Item(
            appInfo = appInfo,
            onItemClicked = { info ->
                println("Uninstalling $info")
                info.uninstall(context)
            }
        )

        val existingExclusion = exclusionManager
            .currentExclusions()
            .filterIsInstance<Exclusion.Package>()
            .firstOrNull { it.match(appInfo.id) }

        val excludeAction = ExcludeActionVH.Item(
            appInfo = appInfo,
            exclusion = existingExclusion,
            onExclude = {
                launch {
                    if (existingExclusion != null) {
                        AppActionDialogDirections.actionAppActionDialogToExclusionActionDialog(
                            existingExclusion.id
                        ).navigate()
                    } else {
                        val newExcl = PackageExclusion(pkgId = appInfo.id)
                        exclusionManager.save(newExcl)
                    }
                }
            },
            onEdit = {
                AppActionDialogDirections.actionAppActionDialogToExclusionActionDialog(it.id).navigate()
            }
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

        val disableAction = if (rootManager.useRoot()) {
            ToggleActionVH.Item(
                appInfo = appInfo,
                onToggle = {
                    val task = AppControlToggleTask(setOf(appInfo.pkg.id))
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
                uninstallAction,
                systemSettingsAction,
                appStoreAction,
                excludeAction,
                disableAction,
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
        private val TAG = logTag("AppControl", "Action", "Dialog", "VM")
    }

}