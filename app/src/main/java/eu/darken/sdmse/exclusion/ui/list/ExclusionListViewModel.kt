package eu.darken.sdmse.exclusion.ui.list

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.MimeTypes
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.pkgs
import eu.darken.sdmse.common.readAsText
import eu.darken.sdmse.common.uix.ViewModel3
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
import eu.darken.sdmse.exclusion.ui.list.types.PackageExclusionVH
import eu.darken.sdmse.exclusion.ui.list.types.PathExclusionVH
import eu.darken.sdmse.exclusion.ui.list.types.SegmentExclusionVH
import eu.darken.sdmse.systemcleaner.ui.customfilter.list.CustomFilterListFragmentDirections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class ExclusionListViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
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
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val events = SingleLiveEvent<ExclusionListEvents>()

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

    val state = combine(
        exclusionManager.exclusions,
        pkgRepo.pkgs().onStart { emit(emptySet()) },
        lookups.onStart { emit(emptyMap()) },
        showDefaults,
    ) { holders, pkgs, lookups, showDefaults ->
        handle["showDefaults"] = showDefaults

        val items = mutableListOf<ExclusionListAdapter.Item>()

        holders.mapNotNull { holder ->
            val isDefault = when (holder) {
                is DefaultExclusion -> true
                is UserExclusion -> false
            }
            if (isDefault && !showDefaults) return@mapNotNull null

            when (val exclusion = holder.exclusion) {
                is PkgExclusion -> PackageExclusionVH.Item(
                    pkg = pkgs.firstOrNull { it.id == exclusion.pkgId },
                    exclusion = exclusion,
                    isDefault = isDefault,
                    onItemClick = {
                        if (isDefault) {
                            webpageTool.open((holder as DefaultExclusion).reason)
                        } else {
                            ExclusionListFragmentDirections.actionExclusionsListFragmentToPkgExclusionFragment(
                                exclusionId = exclusion.id,
                                initial = null
                            ).navigate()
                        }
                    }
                )

                is PathExclusion -> PathExclusionVH.Item(
                    lookup = lookups[exclusion.path],
                    exclusion = exclusion,
                    isDefault = isDefault,
                    onItemClick = {
                        if (isDefault) {
                            webpageTool.open((holder as DefaultExclusion).reason)
                        } else {
                            ExclusionListFragmentDirections.actionExclusionsListFragmentToPathExclusionFragment(
                                exclusionId = exclusion.id,
                                initial = null
                            ).navigate()
                        }
                    }
                )

                is SegmentExclusion -> SegmentExclusionVH.Item(
                    exclusion = exclusion,
                    isDefault = isDefault,
                    onItemClick = {
                        if (isDefault) {
                            webpageTool.open((holder as DefaultExclusion).reason)
                        } else {
                            ExclusionListFragmentDirections.actionExclusionsListFragmentToSegmentExclusionFragment(
                                exclusionId = exclusion.id,
                                initial = null
                            ).navigate()
                        }
                    }
                )

                else -> throw NotImplementedError()
            }
        }.run { items.addAll(this) }

        val sortedItems = items.sortedWith(
            compareBy<ExclusionListAdapter.Item> { it.isDefault }
                .thenBy {
                    when (it) {
                        is PackageExclusionVH.Item -> 0
                        is PathExclusionVH.Item -> 1
                        is SegmentExclusionVH.Item -> 2
                        else -> -1
                    }
                }.thenBy {
                    it.exclusion.label.get(context)
                }
        )
        State(
            items = sortedItems,
            loading = false,
            showDefaults = showDefaults,
        )
    }
        .onStart { emit(State()) }
        .asLiveData2()

    data class State(
        val items: List<ExclusionListAdapter.Item> = emptyList(),
        val loading: Boolean = true,
        val showDefaults: Boolean = false,
    )

    fun restore(items: Set<Exclusion>) = launch {
        log(TAG) { "restore(${items.size})" }
        exclusionManager.save(items)
    }

    fun remove(items: List<ExclusionListAdapter.Item>) = launch {
        log(TAG) { "remove(${items.size})" }
        val exclusions = items.map { it.exclusion }.toSet()
        exclusionManager.remove(exclusions.map { it.id }.toSet())
        events.postValue(ExclusionListEvents.UndoRemove(exclusions))
    }

    fun resetDefaultExclusions() = launch {
        log(TAG) { "resetDefaultExclusions()" }
        defaultExclusions.reset()
    }

    fun importExclusions(uris: Collection<Uri>? = null) = launch {
        log(TAG) { "importExclusions($uris)" }
        if (uris == null) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = MimeTypes.Json.value
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            events.postValue(ExclusionListEvents.ImportEvent(intent))
            return@launch
        }

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
            errorEvents.postValue(e)
        }

        events.postValue(ExclusionListEvents.ImportSuccess(exclusion))
    }

    private var stagedExportIds: Set<ExclusionId>?
        set(value) {
            handle["stagedExportIds"] = value
        }
        get() = handle["stagedExportIds"]

    fun exportExclusions(items: Collection<ExclusionListAdapter.Item>) = launch {
        log(TAG) { "exportExclusions($items)" }

        if (!upgradeRepo.isPro()) {
            log(TAG) { "Pro upgrade required" }
            CustomFilterListFragmentDirections.goToUpgradeFragment().navigate()
            return@launch
        }

        val exclusion = items.map { it.exclusion.id }
        stagedExportIds = exclusion.toSet()

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        events.postValue(ExclusionListEvents.ExportEvent(intent, exclusion))
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
            ?: throw IOException("Failed to create ${filename} in $saveDir")

        val rawContainer = exclusionImporter.export(exportData)
        context.contentResolver.openOutputStream(targetFile.uri)?.use { out ->
            out.write(rawContainer.toByteArray())
        }

        log(TAG, VERBOSE) { "Wrote $rawContainer to ${targetFile.uri}" }

        events.postValue(ExclusionListEvents.ExportSuccess(exportData))
    }

    fun showDefeaultExclusions(show: Boolean) {
        log(TAG) { "showDefaultExclusions($show)" }
        showDefaults.value = show
    }

    companion object {
        private val TAG = logTag("Exclusions", "List", "ViewModel")
    }
}