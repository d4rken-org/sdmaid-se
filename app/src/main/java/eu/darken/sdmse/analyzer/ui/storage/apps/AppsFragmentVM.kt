package eu.darken.sdmse.analyzer.ui.storage.apps

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.ui.storage.storage.StorageContentFragmentArgs
import eu.darken.sdmse.appcontrol.core.*
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AppsFragmentVM @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val analyzer: Analyzer,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<StorageContentFragmentArgs>()
    private val targetStorageId = navArgs.storageId

    val state = combine(
        analyzer.data,
        analyzer.progress,
    ) { data, progress ->
        val storage = data.storages.single { it.id == targetStorageId }
        val contents = data.categories[targetStorageId]!!.filterIsInstance<AppCategory>().single()

        State(
            storage = storage,
            apps = contents.pkgStats
                .map { (installId, pkgStat) ->
                    AppsItemVH.Item(
                        pkgStat = pkgStat,
                        onItemClicked = {
                            AppsFragmentDirections.actionAppsFragmentToAppDetailsFragment(
                                storageId = storage.id,
                                installId = installId,
                            ).navigate()
                        }
                    )
                }
                .sortedByDescending { it.pkgStat.totalSize },
            progress = progress,
        )
    }.asLiveData2()

    data class State(
        val storage: DeviceStorage,
        val apps: List<AppsItemVH.Item>?,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("Analyzer", "Content", "Apps", "Fragment", "VM")
    }
}