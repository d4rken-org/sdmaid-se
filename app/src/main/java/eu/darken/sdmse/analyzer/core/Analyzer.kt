package eu.darken.sdmse.analyzer.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.analyzer.core.content.ContentDeleteTask
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.device.DeviceStorageScanTask
import eu.darken.sdmse.analyzer.core.device.DeviceStorageScanner
import eu.darken.sdmse.analyzer.core.storage.StorageScanTask
import eu.darken.sdmse.analyzer.core.storage.StorageScanner
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.core.storage.categories.ContentCategory
import eu.darken.sdmse.analyzer.core.storage.categories.MediaCategory
import eu.darken.sdmse.analyzer.core.storage.categories.SystemCategory
import eu.darken.sdmse.analyzer.core.storage.toFlatContent
import eu.darken.sdmse.analyzer.core.storage.toNestedContent
import eu.darken.sdmse.common.collections.mutate
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.deleteAll
import eu.darken.sdmse.common.files.filterDistinctRoots
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.matches
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class Analyzer @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val deviceScanner: Provider<DeviceStorageScanner>,
    private val storageScanner: Provider<StorageScanner>,
    private val gatewaySwitch: GatewaySwitch,
) : SDMTool, Progress.Client {

    override val type: SDMTool.Type = SDMTool.Type.ANALYZER

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val storageDevices = MutableStateFlow(emptySet<DeviceStorage>())
    private val storageCategories = MutableStateFlow(emptyMap<StorageId, Collection<ContentCategory>>())
    val data: Flow<Data> = combine(
        storageDevices,
        storageCategories,
    ) { storages, categories ->
        val allGroups = categories
            .map { category ->
                category.value
                    .map { it.groups }
                    .flatten()
                    .map { it.id to it }

            }
            .flatten()
            .toMap()

        Data(
            storages = storages,
            categories = categories,
            groups = allGroups
        )
    }

    override val state: Flow<State> = combine(
        data,
        progress,
    ) { data, progress ->
        State(
            data = data,
            progress = progress,
        )
    }.replayingShare(appScope)

    private val jobLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = jobLock.withLock {
        task as AnalyzerTask
        log(TAG) { "submit($task) starting..." }
        updateProgress { Progress.DEFAULT_STATE }
        try {
            val result = when (task) {
                is DeviceStorageScanTask -> scanStorageDevices(task)
                is StorageScanTask -> scanStorageContents(task)
                is ContentDeleteTask -> deleteContent(task)
                else -> throw UnsupportedOperationException("Unsupported task: $task")
            }

            log(TAG, INFO) { "submit($task) finished: $result" }
            result
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun scanStorageDevices(task: DeviceStorageScanTask): DeviceStorageScanTask.Result {
        log(TAG, VERBOSE) { "scanStorageDevices(): $task" }

        storageDevices.value = emptySet()
        storageCategories.value = emptyMap()

        val scanner = deviceScanner.get()
        val storages = scanner.withProgress(this) { scan() }

        storageDevices.value = storages

        return DeviceStorageScanTask.Result(itemCount = storages.size)
    }

    private suspend fun scanStorageContents(task: StorageScanTask): DeviceStorageScanTask.Result {
        log(TAG, VERBOSE) { "scanStorageContents(): $task" }
        val target = storageDevices.value.singleOrNull { it.id == task.target }
            ?: throw IllegalStateException("Couldn't find ${task.target}")

        val scanner = storageScanner.get()

        val start = System.currentTimeMillis()

        val categories = scanner.withProgress(this) { scan(target) }

        val stop = System.currentTimeMillis()
        log(TAG) { "scanStorageContents() took ${stop - start}ms" }

        storageCategories.value = storageCategories.value.mutate {
            this[target.id] = categories
        }

        return DeviceStorageScanTask.Result(itemCount = 0)
    }

    private suspend fun deleteContent(task: ContentDeleteTask): ContentDeleteTask.Result {
        log(TAG, VERBOSE) { "deleteContent(): $task" }

        updateProgressPrimary {
            it.getString(
                eu.darken.sdmse.common.R.string.general_progress_deleting,
                it.getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_items, task.targets.size)
            )
        }

        task.targets
            .filterDistinctRoots()
            .forEach { target ->
                log(TAG) { "Deleting $target" }
                updateProgressSecondary(target.userReadablePath)
                target.deleteAll(gatewaySwitch)
            }

        // TODO this seems convoluted, can we come up with a better data pattern?
        var _oldGroup: ContentGroup? = null
        val oldCategory: ContentCategory = storageCategories.value[task.storageId]!!.singleOrNull { category ->
            category.groups.singleOrNull { it.id == task.groupId }
                ?.also { _oldGroup = it }
                ?.let { true } ?: false
        } ?: throw IllegalStateException("Can't find category and group for ${task.groupId}")
        val oldGroup = _oldGroup!!
        var freedSpace = 0L
        val newContents = oldGroup.contents
            .toFlatContent()
            .filter { item ->
                val deleted = task.targets.any { it.isAncestorOf(item.path) || it.matches(item.path) }
                if (deleted) freedSpace += item.itemSize ?: 0L
                !deleted
            }
            .toNestedContent()

        val newGroup = oldGroup.copy(contents = newContents)

        val newCategory = when (oldCategory) {
            is AppCategory -> {
                val oldPkg = oldCategory.pkgStats[task.targetPkg]!!
                val newPkg = when {
                    oldPkg.appCode == oldGroup -> oldPkg.copy(appCode = newGroup)
                    oldPkg.appData == oldGroup -> oldPkg.copy(appData = newGroup)
                    oldPkg.appMedia == oldGroup -> oldPkg.copy(appMedia = newGroup)
                    oldPkg.extraData == oldGroup -> oldPkg.copy(extraData = newGroup)
                    else -> throw IllegalArgumentException("${oldPkg.id} has no matching content group")
                }
                oldCategory.copy(
                    pkgStats = oldCategory.pkgStats.mutate {
                        this[oldPkg.id] = newPkg
                    }
                )
            }

            is MediaCategory -> oldCategory.copy(groups = oldCategory.groups.minus(oldGroup).plus(newGroup))

            is SystemCategory -> {
                throw UnsupportedOperationException("SystemCategory???")
            }
        }

        storageCategories.value = storageCategories.value.mutate {
            this[task.storageId] = this[task.storageId]!!.minus(oldCategory).plus(newCategory)
        }


        return ContentDeleteTask.Result(
            itemCount = task.targets.size,
            freedSpace = freedSpace,
        )
    }

    data class State(
        val data: Data,
        val progress: Progress.Data?,
    ) : SDMTool.State

    data class Data(
        val storages: Set<DeviceStorage> = emptySet(),
        val categories: Map<StorageId, Collection<ContentCategory>> = emptyMap(),
        val groups: Map<ContentGroup.Id, ContentGroup> = emptyMap(),
    )

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Analyzer): SDMTool
    }

    companion object {
        private val TAG = logTag("Analyzer")
    }
}