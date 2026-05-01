package eu.darken.sdmse.exclusion.ui.list

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.MimeTypes
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.pkgs
import eu.darken.sdmse.common.readAsText
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.exclusion.core.DefaultExclusions
import eu.darken.sdmse.exclusion.core.ExclusionImporter
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.LegacyImporter
import eu.darken.sdmse.exclusion.core.current
import eu.darken.sdmse.exclusion.core.types.DefaultExclusion
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.exclusion.core.types.SegmentExclusion
import eu.darken.sdmse.exclusion.core.types.UserExclusion
import eu.darken.sdmse.exclusion.ui.PathExclusionEditorRoute
import eu.darken.sdmse.exclusion.ui.PkgExclusionEditorRoute
import eu.darken.sdmse.exclusion.ui.SegmentExclusionEditorRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class ExclusionListViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val exclusionManager: ExclusionManager,
    private val pkgRepo: PkgRepo,
    private val gatewaySwitch: GatewaySwitch,
    private val defaultExclusions: DefaultExclusions,
    private val webpageTool: WebpageTool,
    private val upgradeRepo: UpgradeRepo,
    private val legacyImporter: LegacyImporter,
    private val exclusionImporter: ExclusionImporter,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    val events = SingleEventFlow<Event>()

    private val showDefaults = MutableStateFlow(handle["showDefaults"] ?: false)

    private val lookups = exclusionManager.exclusions
        .map { holders -> holders.map { it.exclusion } }
        .map { excls -> excls.filterIsInstance<PathExclusion>().map { it.path } }
        .map { paths ->
            paths
                .mapNotNull { path ->
                    try {
                        gatewaySwitch.lookup(path)
                    } catch (e: Exception) {
                        log(TAG, VERBOSE) { "Path exclusion lookup failed: $e" }
                        null
                    }
                }
                .groupBy { it.lookedUp }
                .mapValues { it.value.first() }
        }

    val state: StateFlow<State> = combine(
        exclusionManager.exclusions,
        pkgRepo.pkgs().onStart { emit(emptySet()) },
        lookups.onStart { emit(emptyMap()) },
        showDefaults,
    ) { holders, pkgs, lookups, showDefaults ->
        handle["showDefaults"] = showDefaults

        val rows = mutableListOf<Row>()
        holders.forEach { holder ->
            val isDefault = when (holder) {
                is DefaultExclusion -> true
                is UserExclusion -> false
            }
            if (isDefault && !showDefaults) return@forEach

            val reasonUrl = (holder as? DefaultExclusion)?.reason

            when (val exclusion = holder.exclusion) {
                is PkgExclusion -> rows.add(
                    Row.Pkg(
                        exclusion = exclusion,
                        pkg = pkgs.firstOrNull { it.id == exclusion.pkgId },
                        isDefault = isDefault,
                        reasonUrl = reasonUrl,
                        label = exclusion.label.get(context),
                    ),
                )

                is PathExclusion -> rows.add(
                    Row.Path(
                        exclusion = exclusion,
                        lookup = lookups[exclusion.path],
                        isDefault = isDefault,
                        reasonUrl = reasonUrl,
                        label = exclusion.label.get(context),
                    ),
                )

                is SegmentExclusion -> rows.add(
                    Row.Segment(
                        exclusion = exclusion,
                        isDefault = isDefault,
                        reasonUrl = reasonUrl,
                        label = exclusion.label.get(context),
                    ),
                )

                else -> Unit
            }
        }

        val sorted = rows.sortedWith(
            compareBy<Row> { it.isDefault }
                .thenBy {
                    when (it) {
                        is Row.Pkg -> 0
                        is Row.Path -> 1
                        is Row.Segment -> 2
                    }
                }
                .thenBy { it.label },
        )
        State(rows = sorted, showDefaults = showDefaults)
    }
        .safeStateIn(
            initialValue = State(),
            onError = { State(rows = emptyList()) },
        )

    fun onRowClick(row: Row) {
        log(TAG) { "onRowClick($row)" }
        if (row.isDefault) {
            row.reasonUrl?.let { webpageTool.open(it) }
            return
        }
        when (row) {
            is Row.Pkg -> navTo(PkgExclusionEditorRoute(exclusionId = row.exclusion.id))
            is Row.Path -> navTo(PathExclusionEditorRoute(exclusionId = row.exclusion.id))
            is Row.Segment -> navTo(SegmentExclusionEditorRoute(exclusionId = row.exclusion.id))
        }
    }

    fun restore(items: Set<Exclusion>) = launch {
        log(TAG) { "restore(${items.size})" }
        exclusionManager.save(items)
    }

    fun removeByIds(ids: Set<ExclusionId>) = launch {
        log(TAG) { "removeByIds(${ids.size})" }
        val exclusions = exclusionManager.current().filter { ids.contains(it.id) }.toSet()
        exclusionManager.remove(exclusions.map { it.id }.toSet())
        events.emit(Event.UndoRemove(exclusions))
    }

    fun resetDefaultExclusions() = launch {
        log(TAG) { "resetDefaultExclusions()" }
        defaultExclusions.reset()
    }

    fun openHelp() = launch {
        webpageTool.open("https://github.com/d4rken-org/sdmaid-se/wiki/Exclusions")
    }

    fun requestImport() = launch {
        log(TAG) { "requestImport()" }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = MimeTypes.Json.value
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        events.emit(Event.ImportEvent(intent))
    }

    fun importExclusions(uris: Collection<Uri>) = launch {
        log(TAG) { "importExclusions($uris)" }
        val exclusion = uris
            .map {
                val result = it.readAsText(context) ?: throw IllegalArgumentException("Failed to read $it")
                log(TAG) { "Read $it ->  $result" }
                result
            }
            .mapNotNull { raw ->
                try {
                    exclusionImporter.import(raw).also {
                        log(TAG, INFO) { "Imported ${it.size}: $it" }
                    }
                } catch (e: Exception) {
                    log(TAG, WARN) { "This is not valid exclusion data, maybe legacy data?" }
                    try {
                        legacyImporter.tryConvert(raw).also {
                            log(TAG, INFO) { "Imported (legacy) ${it.size}: $it" }
                        }
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Legacy import failed for $raw:\n${e.asLog()}" }
                        null
                    }
                }
            }
            .flatten()
            .toSet()

        try {
            exclusionManager.save(exclusion)
        } catch (e: Exception) {
            errorEvents.emit(e)
        }

        events.emit(Event.ImportSuccess(exclusion))
    }

    private var stagedExportIds: Set<ExclusionId>?
        set(value) {
            handle["stagedExportIds"] = value?.toTypedArray()
        }
        get() = handle.get<Array<ExclusionId>>("stagedExportIds")?.toSet()

    fun exportExclusions(ids: Set<ExclusionId>) = launch {
        log(TAG) { "exportExclusions($ids)" }

        if (!upgradeRepo.isPro()) {
            log(TAG) { "Pro upgrade required" }
            navTo(UpgradeRoute())
            return@launch
        }

        stagedExportIds = ids
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        events.emit(Event.ExportEvent(intent))
    }

    fun performExport(directoryUri: Uri?) = launch {
        if (directoryUri == null) {
            log(TAG, WARN) { "Export failed, no path picked" }
            return@launch
        }

        val exportIds = stagedExportIds ?: throw IllegalStateException("No staged export data available")

        val exportData = exclusionManager.current().filter { exportIds.contains(it.id) }.toSet()

        val saveDir = DocumentFile.fromTreeUri(context, directoryUri)
            ?: throw IOException("Failed to access $directoryUri")

        val filename = "SD Maid 2/SE Exclusions ${System.currentTimeMillis()}"

        val targetFile = saveDir.createFile(MimeTypes.Json.value, filename)
            ?: throw IOException("Failed to create $filename in $saveDir")

        val rawContainer = exclusionImporter.export(exportData)
        context.contentResolver.openOutputStream(targetFile.uri)?.use { out ->
            out.write(rawContainer.toByteArray())
        }

        log(TAG, VERBOSE) { "Wrote $rawContainer to ${targetFile.uri}" }

        events.emit(Event.ExportSuccess(exportData))
    }

    fun showDefaultExclusions(show: Boolean) {
        log(TAG) { "showDefaultExclusions($show)" }
        showDefaults.value = show
    }

    fun openAppControl() {
        navTo(eu.darken.sdmse.common.navigation.routes.AppControlListRoute)
    }

    fun openStoragePicker() {
        navTo(eu.darken.sdmse.common.navigation.routes.DeviceStorageRoute)
    }

    fun openSegmentEditor() {
        navTo(
            SegmentExclusionEditorRoute(
                exclusionId = null,
                initial = eu.darken.sdmse.exclusion.ui.editor.segment.SegmentExclusionEditorOptions(),
            ),
        )
    }

    data class State(
        val rows: List<Row>? = null,
        val showDefaults: Boolean = false,
    )

    sealed interface Row {
        val exclusion: Exclusion
        val isDefault: Boolean
        val reasonUrl: String?
        val label: String
        val stableId: String get() = exclusion.id

        data class Pkg(
            override val exclusion: PkgExclusion,
            val pkg: eu.darken.sdmse.common.pkgs.Pkg?,
            override val isDefault: Boolean,
            override val reasonUrl: String?,
            override val label: String,
        ) : Row

        data class Path(
            override val exclusion: PathExclusion,
            val lookup: APathLookup<*>?,
            override val isDefault: Boolean,
            override val reasonUrl: String?,
            override val label: String,
        ) : Row

        data class Segment(
            override val exclusion: SegmentExclusion,
            override val isDefault: Boolean,
            override val reasonUrl: String?,
            override val label: String,
        ) : Row
    }

    sealed interface Event {
        data class UndoRemove(val exclusions: Set<Exclusion>) : Event
        data class ImportEvent(val intent: Intent) : Event
        data class ExportEvent(val intent: Intent) : Event
        data class ImportSuccess(val exclusions: Set<Exclusion>) : Event
        data class ExportSuccess(val exclusions: Set<Exclusion>) : Event
    }

    companion object {
        private val TAG = logTag("Exclusions", "List", "ViewModel")
    }
}
