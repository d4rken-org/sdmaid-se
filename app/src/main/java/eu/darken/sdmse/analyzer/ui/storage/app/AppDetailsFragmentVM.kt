package eu.darken.sdmse.analyzer.ui.storage.app

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
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
        val appContent = data.categories[targetStorageId]!!.filterIsInstance<AppCategory>().single()
        val pkgStat = appContent.pkgStats[targetInstallId]!!

        val items = mutableListOf<AppDetailsAdapter.Item>()

        AppDetailsHeaderVH.Item(
            storage = storage,
            pkgStat = pkgStat,
        ).run { items.add(this) }

        pkgStat.appCode
            ?.let {
                AppDetailsAppCodeVH.Item(
                    storage = storage,
                    pkgStat = pkgStat,
                    group = it,
                    onViewAction = {
                        AppDetailsFragmentDirections.actionAppDetailsFragmentToContentFragment(
                            storageId = targetStorageId,
                            groupId = pkgStat.appCode.id,
                            installId = pkgStat.id,
                        ).navigate()
                    }
                )
            }
            ?.run { items.add(this) }

        pkgStat.privateData
            ?.let {
                AppDetailsPrivateDataVH.Item(
                    storage = storage,
                    pkgStat = pkgStat,
                    group = it,
                    onViewAction = {
                        AppDetailsFragmentDirections.actionAppDetailsFragmentToContentFragment(
                            storageId = targetStorageId,
                            groupId = pkgStat.privateData.id,
                            installId = pkgStat.id,
                        ).navigate()
                    }
                )
            }
            ?.run { items.add(this) }

        pkgStat.publicData
            ?.let {
                AppDetailsPublicDataVH.Item(
                    storage = storage,
                    pkgStat = pkgStat,
                    group = it,
                    onViewAction = {
                        AppDetailsFragmentDirections.actionAppDetailsFragmentToContentFragment(
                            storageId = targetStorageId,
                            groupId = pkgStat.publicData.id,
                            installId = pkgStat.id,
                        ).navigate()
                    }
                )
            }
            ?.run { items.add(this) }

        pkgStat.extraData
            ?.let {
                AppDetailsExtraDataVH.Item(
                    storage = storage,
                    pkgStat = pkgStat,
                    group = it,
                    onViewAction = {
                        AppDetailsFragmentDirections.actionAppDetailsFragmentToContentFragment(
                            storageId = targetStorageId,
                            groupId = pkgStat.extraData.id,
                            installId = pkgStat.id,
                        ).navigate()
                    }
                )
            }
            ?.run { items.add(this) }

        State(
            storage = storage,
            pkgStat = pkgStat,
            items = items,
            progress = progress,
        )
    }.asLiveData2()

    data class State(
        val storage: DeviceStorage,
        val pkgStat: AppCategory.PkgStat,
        val items: List<AppDetailsAdapter.Item>?,
        val progress: Progress.Data?,
    )

    companion object {
        private val TAG = logTag("Analyzer", "App", "Details", "Fragment", "VM")
    }
}