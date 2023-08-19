package eu.darken.sdmse.systemcleaner.ui.customfilter.list

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.MimeTypes
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.readAsText
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.main.ui.dashboard.items.*
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterRepo
import eu.darken.sdmse.systemcleaner.core.filter.custom.RawFilter
import eu.darken.sdmse.systemcleaner.core.filter.custom.toggleCustomFilter
import eu.darken.sdmse.systemcleaner.ui.customfilter.list.types.CustomFilterDefaultVH
import kotlinx.coroutines.flow.*
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class CustomFilterListViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @Suppress("StaticFieldLeak") @ApplicationContext private val context: Context,
    private val customFilterRepo: CustomFilterRepo,
    private val systemCleanerSettings: SystemCleanerSettings,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val events = SingleLiveEvent<CustomFilterListEvents>()

    val state = combine(
        customFilterRepo.configs,
        upgradeRepo.upgradeInfo
            .map {
                @Suppress("USELESS_CAST")
                it as UpgradeRepo.Info?
            }
            .onStart { emit(null) },
        systemCleanerSettings.enabledCustomFilter.flow,
    ) { configs, upgradeInfo, enabledFilters ->
        val items = configs.map { config ->
            CustomFilterDefaultVH.Item(
                config = config,
                isEnabled = enabledFilters.contains(config.identifier),
                onItemClick = {
                    launch {
                        systemCleanerSettings.toggleCustomFilter(config.identifier)
                    }
                },
                onEditClick = { edit(it) }
            )
        }
        val sortedItems = items.sortedBy { it.config.label }
        State(
            sortedItems,
            loading = false,
            isPro = upgradeInfo?.isPro
        )
    }
        .onStart { emit(State()) }
        .asLiveData2()

    data class State(
        val items: List<CustomFilterListAdapter.Item> = emptyList(),
        val loading: Boolean = true,
        val isPro: Boolean? = null,
    )

    fun restore(items: Set<CustomFilterConfig>) = launch {
        log(TAG) { "restore(${items.size})" }
        customFilterRepo.save(items)
    }

    fun remove(items: List<CustomFilterListAdapter.Item>) = launch {
        log(TAG) { "remove(${items.size})" }
        val configs = items.map { it.config }.toSet()
        customFilterRepo.remove(configs.map { it.identifier }.toSet())
        events.postValue(CustomFilterListEvents.UndoRemove(configs))
    }

    fun edit(item: CustomFilterListAdapter.Item) = launch {
        log(TAG) { "edit($item)" }

        if (!upgradeRepo.isPro()) {
            log(TAG) { "Pro upgrade required" }
            CustomFilterListFragmentDirections.goToUpgradeFragment().navigate()
            return@launch
        }

        CustomFilterListFragmentDirections.actionCustomFilterListFragmentToCustomFilterEditorFragment(
            identifier = item.config.identifier
        ).navigate()
    }

    fun importFilter(uris: Collection<Uri>? = null) = launch {
        log(TAG) { "importFilter($uris)" }
        if (uris == null) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = MimeTypes.Json.value
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            events.postValue(CustomFilterListEvents.ImportEvent(intent))
            return@launch
        }

        val rawFilter = uris
            .mapNotNull {
                val result = it.readAsText(context) ?: throw IllegalArgumentException("Failed to read $it")
                log(TAG) { "Read $it: $result" }
                it to result
            }
            .map { RawFilter(it.first.toString(), it.second) }

        try {
            customFilterRepo.importFilter(rawFilter)
        } catch (e: Exception) {
            errorEvents.postValue(e)
        }
    }

    private var stagedExport: Collection<RawFilter>? = null
    fun exportFilter(items: Collection<CustomFilterListAdapter.Item>) = launch {
        log(TAG) { "exportFilter($items)" }

        if (!upgradeRepo.isPro()) {
            log(TAG) { "Pro upgrade required" }
            CustomFilterListFragmentDirections.goToUpgradeFragment().navigate()
            return@launch
        }

        val rawFilter = customFilterRepo.exportFilters(items.map { it.config.identifier })
        stagedExport = rawFilter

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        events.postValue(CustomFilterListEvents.ExportEvent(intent, rawFilter))
    }

    fun performExport(directoryUri: Uri?) = launch {
        if (directoryUri == null) {
            log(TAG, WARN) { "Export failed, no path picked" }
            return@launch
        }

        val exportData = stagedExport ?: throw IllegalStateException("No staged export data available")

        val saveDir = DocumentFile.fromTreeUri(context, directoryUri)
            ?: throw IOException("Failed to access $directoryUri")

        exportData.forEach { rawFilter ->
            val targetFile = saveDir.createFile(MimeTypes.Json.value, rawFilter.name)
                ?: throw IOException("Failed to create ${rawFilter.name} in $saveDir")

            context.contentResolver.openOutputStream(targetFile.uri)?.use { out ->
                out.write(rawFilter.payload.toByteArray())
            }

            log(TAG) { "Wrote ${rawFilter.name} to $targetFile" }
        }
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "CustomFilter", "List", "ViewModel")
    }
}