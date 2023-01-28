package eu.darken.sdmse.appcontrol.ui.list.actions

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppInfo
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
    private val appControl: AppControl,
) : ViewModel3(dispatcherProvider) {
    //
    private val navArgs by handle.navArgs<AppActionDialogArgs>()
    private val pkgId: Pkg.Id = navArgs.pkgId

    val state = appControl.data
        .mapNotNull { data -> data?.apps?.singleOrNull { it.pkg.id == pkgId } }
        .map { appInfo ->
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
                    disableAction
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