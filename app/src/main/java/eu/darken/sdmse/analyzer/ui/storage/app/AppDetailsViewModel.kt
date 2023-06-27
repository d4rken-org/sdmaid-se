package eu.darken.sdmse.analyzer.ui.storage.app

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsAppCodeVH
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsAppDataVH
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsAppMediaVH
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsExtraDataVH
import eu.darken.sdmse.analyzer.ui.storage.app.items.AppDetailsHeaderVH
import eu.darken.sdmse.appcontrol.core.*
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.pkgs.getSettingsIntent
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AppDetailsViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @Suppress("StaticFieldLeak") @ApplicationContext private val context: Context,
    private val analyzer: Analyzer,
) : ViewModel3(dispatcherProvider) {

    private val navArgs by handle.navArgs<AppDetailsFragmentArgs>()
    private val targetStorageId = navArgs.storageId
    private val targetInstallId = navArgs.installId

    init {
        // Handle process death+restore
        analyzer.data
            .filter { it.findPkg() == null }
            .take(1)
            .onEach {
                log(TAG, WARN) { "Can't find app for $targetInstallId on $targetStorageId" }
                popNavStack()
            }
            .launchInViewModel()
    }

    private fun Analyzer.Data.findPkg(): AppCategory.PkgStat? {
        val appContent = categories[targetStorageId]?.filterIsInstance<AppCategory>()?.singleOrNull()
        return appContent?.pkgStats?.get(targetInstallId)
    }

    val state = combine(
        // Handle process death+restore
        analyzer.data.filter { it.findPkg() != null },
        analyzer.progress,
    ) { data, progress ->
        val storage = data.storages.single { it.id == targetStorageId }
        val pkgStat = data.findPkg()!!

        val items = mutableListOf<AppDetailsAdapter.Item>()

        AppDetailsHeaderVH.Item(
            storage = storage,
            pkgStat = pkgStat,
            onSettingsClicked = {
                val intent = pkgStat.pkg.getSettingsIntent(context).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Launching system settings intent failed: ${e.asLog()}" }
                    errorEvents.postValue(e)
                }
            }
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

        pkgStat.appData
            ?.let {
                AppDetailsAppDataVH.Item(
                    storage = storage,
                    pkgStat = pkgStat,
                    group = it,
                    onViewAction = {
                        AppDetailsFragmentDirections.actionAppDetailsFragmentToContentFragment(
                            storageId = targetStorageId,
                            groupId = pkgStat.appData.id,
                            installId = pkgStat.id,
                        ).navigate()
                    }
                )
            }
            ?.run { items.add(this) }

        pkgStat.appMedia
            ?.let {
                AppDetailsAppMediaVH.Item(
                    storage = storage,
                    pkgStat = pkgStat,
                    group = it,
                    onViewAction = {
                        AppDetailsFragmentDirections.actionAppDetailsFragmentToContentFragment(
                            storageId = targetStorageId,
                            groupId = pkgStat.appMedia.id,
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
        private val TAG = logTag("Analyzer", "App", "Details", "ViewModel")
    }
}