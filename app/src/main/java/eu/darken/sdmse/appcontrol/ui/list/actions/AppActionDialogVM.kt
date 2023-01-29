package eu.darken.sdmse.appcontrol.ui.list.actions

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.createGooglePlayIntent
import eu.darken.sdmse.appcontrol.core.createSystemSettingsIntent
import eu.darken.sdmse.appcontrol.ui.list.actions.items.AppStoreActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.SystemSettingsActionVH
import eu.darken.sdmse.appcontrol.ui.list.actions.items.ToggleActionVH
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AppActionDialogVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val appControl: AppControl,
) : ViewModel3(dispatcherProvider) {
    //
    private val navArgs by handle.navArgs<AppActionDialogArgs>()
    private val pkgId: Pkg.Id = navArgs.pkgId

    val state = appControl.data
        .mapNotNull { data -> data?.apps?.singleOrNull { it.pkg.id == pkgId } }
        .map { appInfo ->
            val systemSettingsAction = SystemSettingsActionVH.Item(
                appInfo = appInfo,
                onItemClicked = {
                    val intent = it.createSystemSettingsIntent(context)
                    context.startActivity(intent)
                }
            )
            val appStoreAction = AppStoreActionVH.Item(
                appInfo = appInfo,
                onItemClicked = { info ->
                    val intent = info.createGooglePlayIntent(context)
                    context.startActivity(intent)
                }
            )
            val disableAction = ToggleActionVH.Item(
                appInfo = appInfo,
                onItemClicked = {
                    // TODO
                }
            )
            State(
                isWorking = false,
                appInfo = appInfo,
                actions = listOf(
                    systemSettingsAction,
                    appStoreAction,
                    disableAction,
                ).filterNotNull()
            )
        }
        .asLiveData2()

    init {
        appControl.data
            .map { data ->
                data?.apps?.singleOrNull { it.pkg.id == pkgId }
            }
            .filter { it == null }
            .take(1)
            .onEach { popNavStack() }
            .launchInViewModel()
    }

    data class State(
        val isWorking: Boolean,
        val appInfo: AppInfo,
        val actions: List<AppActionAdapter.Item>?,
    )

    companion object {
        private val TAG = logTag("AppControl", "Action", "Dialog", "VM")
    }

}