package eu.darken.sdmse.analyzer.ui.storage.apps

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.ui.storage.storage.StorageContentFragmentArgs
import eu.darken.sdmse.appcontrol.core.*
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AppsViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val analyzer: Analyzer,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<StorageContentFragmentArgs>()
    private val targetStorageId = navArgs.storageId

    init {
        // Handle process death+restore
        analyzer.data
            .filter { it.findAppCategory() == null }
            .take(1)
            .onEach {
                log(TAG, WARN) { "Can't find app category for $targetStorageId" }
                popNavStack()
            }
            .launchInViewModel()
    }

    private fun Analyzer.Data.findAppCategory(): AppCategory? {
        return categories[targetStorageId]?.filterIsInstance<AppCategory>()?.singleOrNull()
    }

    val state = combine(
        // Handle process death+restore
        analyzer.data.filter { it.findAppCategory() != null },
        analyzer.progress,
    ) { data, progress ->
        val storage = data.storages.single { it.id == targetStorageId }
        val category = data.findAppCategory()!!

        State(
            storage = storage,
            apps = category.pkgStats
                .map { (installId, pkgStat) ->
                    AppsItemVH.Item(
                        appCategory = category,
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
        private val TAG = logTag("Analyzer", "Content", "Apps", "ViewModel")
    }
}