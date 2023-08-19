package eu.darken.sdmse.systemcleaner.core.filter.custom

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.serialization.fromFile
import eu.darken.sdmse.common.serialization.toFile
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.sieve.NameCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomFilterRepo @Inject constructor(
    private val context: Context,
    private val baseMoshi: Moshi,
    private val settings: SystemCleanerSettings,
    private val legacyImporter: LegacyFilterSupport,
) {

    private val moshi by lazy {
        baseMoshi.newBuilder().apply {
            add(NameCriterium.MOSHI_ADAPTER_FACTORY)
            add(SegmentCriterium.MOSHI_ADAPTER_FACTORY)
        }.build()
    }
    private val lock = Mutex()
    private val configAdapter by lazy { moshi.adapter<CustomFilterConfig>() }
    private val filterDir by lazy {
        File(context.filesDir, "systemcleaner/customfilter2").apply {
            if (!exists() && mkdirs()) log(TAG) { "Created $this" }
        }
    }

    private val FilterIdentifier.configPath: File
        get() = File(filterDir, "$this.json")

    private val refreshTrigger = MutableStateFlow(UUID.randomUUID())

    val configs: Flow<Collection<CustomFilterConfig>> = refreshTrigger
        .flatMapLatest { _ ->
            flow {
                val configFiles = filterDir.listFiles()!!.let { dirContent ->
                    dirContent.filterNot { it.name.endsWith(".json") }.forEach {
                        log(TAG, WARN) { "Unexpected file: $it" }
                    }
                    dirContent.filter { it.name.endsWith(".json") }
                }
                emit(configFiles)
            }
        }
        .map { files ->
            lock.withLock {
                val configs = files.map { configPath ->
                    configAdapter.fromFile(configPath).also { log(TAG) { "Loaded config $configPath -> $it" } }
                }

                val orphaned = settings.enabledCustomFilter.value().filter { id ->
                    configs.none { it.identifier == id }
                }
                orphaned.forEach {
                    log(TAG, WARN) { "Clearing orphaned filter ID: $it" }
                    settings.clearCustomFilter(it)
                }
                configs
            }
        }

    suspend fun refresh() {
        log(TAG, VERBOSE) { "refresh()" }
        refreshTrigger.value = UUID.randomUUID()
    }

    suspend fun save(configs: Set<CustomFilterConfig>): Unit {
        log(TAG) { "save($configs)" }
        lock.withLock {
            configs.forEach { config ->
                val path = config.identifier.configPath
                configAdapter.toFile(config, path)
                log(TAG) { "Saved to $path" }
            }
        }
        refresh()
    }

    suspend fun remove(ids: Set<FilterIdentifier>): Unit {
        log(TAG) { "remove($ids)" }

        lock.withLock {
            ids.forEach { id ->
                val path = id.configPath
                if (!path.delete()) {
                    if (path.exists()) {
                        log(TAG, ERROR) { "Failed to delete $path" }
                    } else {
                        log(TAG, WARN) { "Config does not exist on disk: $path" }
                    }
                }
                settings.clearCustomFilter(id)
            }
        }

        refresh()
    }

    fun generateIdentifier() = UUID.randomUUID().toString()

    suspend fun importFilter(rawFilters: List<RawFilter>) {
        log(TAG) { "importFilter($rawFilters)" }
        val configs = rawFilters.map { rawFilter ->
            try {
                configAdapter.fromJson(rawFilter.payload)!!
            } catch (ogError: Exception) {
                try {
                    legacyImporter.import(rawFilter.payload)!!
                } catch (_: Exception) {
                    log(TAG, ERROR) { "Failed to import $rawFilter: ${ogError.asLog()}" }
                    throw ogError
                }
            }
        }.toSet()
        save(configs)
    }

    suspend fun exportFilters(identifiers: Collection<FilterIdentifier>): Collection<RawFilter> {
        log(TAG) { "exportFilters($identifiers)" }
        val configs = currentConfigs().filter { identifiers.contains(it.identifier) }

        return configs.map {
            val rawJson = configAdapter.toJson(it)
            RawFilter("${it.label} - ${it.identifier.takeLast(10)}.json", rawJson)
        }
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "CustomFilter", "Repo")
    }
}