package eu.darken.sdmse.analyzer.ui.storage.app

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.types.AppContent
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsAppCodeVH
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsExtraDataVH
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsHeaderVH
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsPrivateDataVH
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsPublicDataVH
import eu.darken.sdmse.appcontrol.core.*
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AppDetailsFragmentVM @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val analyzer: Analyzer,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<AppDetailsFragmentArgs>()
    private val targetStorageId = navArgs.storageId
    private val targetInstallId = navArgs.installId

    val state = combine(
        analyzer.data,
        analyzer.progress,
    ) { data, progress ->
        val storage = data.storages.single { it.id == targetStorageId }
        val appContent = data.contents[targetStorageId]!!.filterIsInstance<AppContent>().single()
        val pkgStat = appContent.pkgStats.single { it.id == targetInstallId }

        val items = mutableListOf<AppDetailsAdapter.Item>()

        AppDetailsHeaderVH.Item(
            storage = storage,
            pkgStat = pkgStat,
        ).run { items.add(this) }

        AppDetailsAppCodeVH.Item(
            storage = storage,
            pkgStat = pkgStat,
            onViewAction = {

            }
        ).run { items.add(this) }

        AppDetailsPrivateDataVH.Item(
            storage = storage,
            pkgStat = pkgStat,
            onViewAction = {

            }
        ).run { items.add(this) }

        AppDetailsPublicDataVH.Item(
            storage = storage,
            pkgStat = pkgStat,
            onViewAction = {

            }
        ).run { items.add(this) }

        AppDetailsExtraDataVH.Item(
            storage = storage,
            pkgStat = pkgStat,
            onViewAction = {

            }
        ).run { items.add(this) }

        State(
            storage = storage,
            pkgStat = pkgStat,
            items = items,
            progress = progress,
        )
    }.asLiveData2()

    data class State(
        val storage: DeviceStorage,
        val pkgStat: AppContent.PkgStat,
        val items: List<AppDetailsAdapter.Item>?,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("Analyzer", "App", "Details", "Fragment", "VM")
    }
}