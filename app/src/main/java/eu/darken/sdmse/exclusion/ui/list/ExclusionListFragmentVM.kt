package eu.darken.sdmse.exclusion.ui.list

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.getPkg
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.PackageExclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.ui.list.types.PackageExclusionVH
import eu.darken.sdmse.exclusion.ui.list.types.PathExclusionVH
import eu.darken.sdmse.main.ui.dashboard.items.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class ExclusionListFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val exclusionManager: ExclusionManager,
    private val pkgRepo: PkgRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {


    val state = exclusionManager.exclusions
        .map { exclusions ->
            val items = exclusions.map { exclusion ->
                when (exclusion) {
                    is PackageExclusion -> PackageExclusionVH.Item(
                        appLabel = pkgRepo.getPkg(exclusion.pkgId).firstOrNull()?.label,
                        exclusion = exclusion,
                        onItemClick = {
                            ExclusionListFragmentDirections.actionExclusionsListFragmentToExclusionActionDialog(
                                exclusion.id
                            ).navigate()
                        }
                    )
                    is PathExclusion -> PathExclusionVH.Item(
                        exclusion = exclusion,
                        onItemClick = {
                            ExclusionListFragmentDirections.actionExclusionsListFragmentToExclusionActionDialog(
                                exclusion.id
                            ).navigate()
                        }
                    )
                    else -> throw NotImplementedError()
                }
            }
            State(items)
        }
        .asLiveData2()

    data class State(
        val items: List<ExclusionListAdapter.Item>
    )

    companion object {
        private val TAG = logTag("Exclusions", "List", "Fragment", "VM")
    }
}