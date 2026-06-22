package eu.darken.sdmse.systemcleaner.ui.customfilter.list

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.MimeTypes
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.filter.CustomFilterEditorOptions
import eu.darken.sdmse.common.filter.CustomFilterEditorRoute
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.readAsText
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterRepo
import eu.darken.sdmse.systemcleaner.core.filter.custom.RawFilter
import eu.darken.sdmse.systemcleaner.core.filter.custom.toggleCustomFilter
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class CustomFilterListViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @Suppress("StaticFieldLeak") @ApplicationContext private val context: Context,
    private val customFilterRepo: CustomFilterRepo,
    private val systemCleanerSettings: SystemCleanerSettings,
    private val upgradeRepo: UpgradeRepo,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    val events = SingleEventFlow<Event>()

    val state: StateFlow<State> = combine(
        customFilterRepo.configs,
        upgradeRepo.upgradeInfo
            .map { it as UpgradeRepo.Info? }
            .onStart { emit(null) },
        systemCleanerSettings.enabledCustomFilter.flow,
    ) { configs, upgradeInfo, enabledFilters ->
        val rows = configs
            .map { config ->
                FilterRow(
                    config = config,
                    isEnabled = enabledFilters.contains(config.identifier),
                )
            }
            .sortedBy { it.config.label }
        State(
            rows = rows,
            loading = false,
            isPro = upgradeInfo?.isPro,
        )
    }.safeStateIn(
        initialValue = State(),
        onError = { State(loading = false) },
    )

    fun onToggleRow(row: FilterRow) = launch {
        systemCleanerSettings.toggleCustomFilter(row.config.identifier)
    }

    fun onEditClick(row: FilterRow) = launch {
        log(TAG) { "onEditClick($row)" }
        if (!upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        navTo(CustomFilterEditorRoute(identifier = row.config.identifier))
    }

    fun onCreateClick() = launch {
        log(TAG) { "onCreateClick()" }
        if (!upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        navTo(
            CustomFilterEditorRoute(
                initial = CustomFilterEditorOptions(),
                identifier = null,
            ),
        )
    }

    fun onHelpClick() {
        webpageTool.open(HELP_URL)
    }

    fun onImportClick() = launch {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = MimeTypes.Json.value
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        events.tryEmit(Event.LaunchImport(intent))
    }

    fun importFilter(uris: Collection<Uri>) = launch {
        log(TAG) { "importFilter($uris)" }
        val raw = uris.map {
            val text = it.readAsText(context) ?: throw IllegalArgumentException("Failed to read $it")
            RawFilter(it.toString(), text)
        }
        try {
            customFilterRepo.importFilter(raw)
        } catch (e: Exception) {
            errorEvents.emit(e)
        }
    }

    fun removeRows(rows: Collection<FilterRow>) = launch {
        log(TAG) { "remove(${rows.size})" }
        val configs = rows.map { it.config }.toSet()
        customFilterRepo.remove(configs.map { it.identifier }.toSet())
        events.tryEmit(Event.UndoRemove(configs))
    }

    fun restore(configs: Set<CustomFilterConfig>) = launch {
        log(TAG) { "restore(${configs.size})" }
        customFilterRepo.save(configs)
    }

    private var stagedExport: Collection<RawFilter>? = null

    fun exportRows(rows: Collection<FilterRow>) = launch {
        log(TAG) { "exportRows($rows)" }
        if (!upgradeRepo.isPro()) {
            navTo(UpgradeRoute())
            return@launch
        }
        val raw = customFilterRepo.exportFilters(rows.map { it.config.identifier })
        stagedExport = raw
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        events.tryEmit(Event.LaunchExport(intent))
    }

    fun performExport(directoryUri: Uri?) = launch {
        if (directoryUri == null) {
            log(TAG, WARN) { "Export failed, no path picked" }
            return@launch
        }
        val data = stagedExport ?: throw IllegalStateException("No staged export data available")
        val saveDir = DocumentFile.fromTreeUri(context, directoryUri)
            ?: throw IOException("Failed to access $directoryUri")
        val exported = mutableListOf<DocumentFile>()
        data.forEach { rawFilter ->
            val target = saveDir.createFile(MimeTypes.Json.value, rawFilter.name)
                ?: throw IOException("Failed to create ${rawFilter.name} in $saveDir")
            context.contentResolver.openOutputStream(target.uri)?.use { out ->
                out.write(rawFilter.payload.toByteArray())
            }
            log(TAG) { "Wrote ${rawFilter.name} to $target" }
            exported.add(target)
        }
        stagedExport = null
        events.tryEmit(Event.ExportFinished(saveDir, exported))
    }

    data class FilterRow(
        val config: CustomFilterConfig,
        val isEnabled: Boolean,
    ) {
        val id: String get() = config.identifier
    }

    data class State(
        val rows: List<FilterRow> = emptyList(),
        val loading: Boolean = true,
        val isPro: Boolean? = null,
    )

    sealed interface Event {
        data class UndoRemove(val configs: Set<CustomFilterConfig>) : Event
        data class LaunchImport(val intent: Intent) : Event
        data class LaunchExport(val intent: Intent) : Event
        data class ExportFinished(val path: DocumentFile, val files: List<DocumentFile>) : Event
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "CustomFilter", "List", "ViewModel")
        private const val HELP_URL = "https://github.com/d4rken-org/sdmaid-se/wiki/SystemCleaner#custom-filter"
    }
}
