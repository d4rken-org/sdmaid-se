package eu.darken.sdmse.main.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.ui.dashboard.items.DebugCardVH
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class DashboardFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val areaManager: DataAreaManager,
    private val pkgOps: PkgOps,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val listItems: LiveData<List<DashboardAdapter.Item>> = flow {
        val items = mutableListOf<DashboardAdapter.Item>()
        if (BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.DEV) {
            DebugCardVH.Item(
                onCheck = {
                    launch {
                        pkgOps.getInstalledPackages()
                    }
                }
            ).run { items.add(this) }
        }

        emit(items)
    }
        .setupCommonEventHandlers(TAG) { "listItems" }
        .asLiveData2()

    companion object {
        private val TAG = logTag("Dashboard", "Fragment", "VM")
    }
}