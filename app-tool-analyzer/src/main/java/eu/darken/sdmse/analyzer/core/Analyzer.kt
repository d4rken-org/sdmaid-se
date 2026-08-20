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
import eu.darken.sdmse.analyzer.core.storage.AppDeepScanTask
import eu.darken.sdmse.analyzer.core.storage.StorageScanTask
import eu.darken.sdmse.analyzer.core.storage.SystemDeepScanTask
import eu.darken.sdmse.analyzer.core.storage.StorageScanner
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.core.storage.categories.ContentCategory
import eu.darken.sdmse.analyzer.core.storage.categories.MediaCategory
import eu.darken.sdmse.analyzer.core.storage.categories.OtherUsersCategory
import eu.darken.sdmse.analyzer.core.storage.categories.SystemCategory
import eu.darken.sdmse.analyzer.core.storage.categories.isContentReadOnly
import eu.darken.sdmse.analyzer.core.storage.categories.ownsGroup
import eu.darken.sdmse.analyzer.core.storage.toFlatContent
import eu.darken.sdmse.analyzer.core.storage.toNestedContent
import eu.darken.sdmse.common.collections.mutate
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.MediaStoreTool
import eu.darken.sdmse.common.files.delete
import eu.darken.sdmse.common.files.filterDistinctRoots
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.matches
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.setup.IncompleteSetupException
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.isComplete
import eu.darken.sdmse.stats.core.SpaceTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import eu.darken.sdmse.setup.SetupBinding
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class Analyzer @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val deviceScanner: Provider<DeviceStorageScanner>,
    private val storageScanner: Provider<StorageScanner>,
    private val gatewaySwitch: GatewaySwitch,
    @SetupBinding(SetupModule.Type.INVENTORY) private val appInventorySetupModule: SetupModule,
    @SetupBinding(SetupModule.Type.STORAGE) private val storageSetupModule: SetupModule,
    private val mediaStoreTool: MediaStoreTool,
    private val spaceTracker: SpaceTracker,
) : SDMTool, Progress.Client {

    override val type: SDMTool.Type = SDMTool.Type.ANALYZER

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    // Storages and categories share a single state holder: a delete publishes new category sizes, a new
    // device free-space value and a recomputed system residual together. Two flows joined by `combine`
    // would expose an intermediate frame carrying some of those and not the others.
    private val coreState = MutableStateFlow(CoreState())
    val data: Flow<Data> = coreState.map { core ->
        val allGroups = core.categories
            .map { category ->
                category.value
                    .map { it.groups }
                    .flatten()
                    .map { it.id to it }

            }
            .flatten()
            .toMap()

        Data(
            storages = core.storages,
            categories = core.categories,
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

    init {
        // Device-storage scans bake `setupIncomplete` into each DeviceStorage at scan time. If a scan
        // ran before the storage permission was granted, that stale flag (and the permission-limited
        // content) sticks in the cached data until the next scan — so the UI keeps showing
        // "permissions missing" and the storage card bounces through a Setup screen that immediately
        // closes. Whenever setup is complete AND the cached data carries the stale flag, re-scan.
        // Observing both flows covers setup completing mid-scan: the finishing scan publishes the
        // stale devices after the setup transition, which re-evaluates the condition. No loop: a
        // successful rescan bakes setupIncomplete=false into every device, and a failed one leaves
        // the devices cleared (scan start empties them) — either way the condition turns false.
        combine(
            storageSetupModule.state.map { it.isComplete }.distinctUntilChanged(),
            coreState.map { it.storages }.distinctUntilChanged(),
        ) { setupComplete, devices ->
            setupComplete && devices.any { it.setupIncomplete }
        }
            .distinctUntilChanged()
            .filter { staleWhileComplete -> staleWhileComplete }
            .onEach {
                log(TAG, INFO) { "Storage setup is complete but cached storage data is stale; re-scanning" }
                try {
                    submit(DeviceStorageScanTask())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Background heal only — appScope has no exception handler, so anything
                    // escaping here would kill the whole process. The next manual scan recovers.
                    log(TAG, ERROR) { "Automatic storage re-scan failed: ${e.asLog()}" }
                }
            }
            .launchIn(appScope)
    }

    private val jobLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = jobLock.withLock {
        task as AnalyzerTask
        log(TAG) { "submit($task) starting..." }
        updateProgress { Progress.Data() }
        try {
            val result = keepResourceHoldersAlive(gatewaySwitch) {
                when (task) {
                    is DeviceStorageScanTask -> scanStorageDevices(task)
                    is StorageScanTask -> scanStorageContents(task)
                    is ContentDeleteTask -> deleteContent(task)
                    is AppDeepScanTask -> deepScanApp(task)
                    is SystemDeepScanTask -> deepScanSystem(task)
                    else -> throw UnsupportedOperationException("Unsupported task: $task")
                }
            }
            log(TAG, INFO) { "submit($task) finished: $result" }
            result
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun scanStorageDevices(task: DeviceStorageScanTask): DeviceStorageScanTask.Result {
        log(TAG, VERBOSE) { "scanStorageDevices(): $task" }

        coreState.update { CoreState() }

        val scanner = deviceScanner.get()
        val storages = scanner.withProgress(this) { scan() }

        coreState.update { it.copy(storages = storages) }
        spaceTracker.recordSnapshot(storages.map {
            SpaceTracker.StorageSnapshot(
                storageId = it.id.externalId.toString(),
                spaceFree = it.spaceFree,
                spaceCapacity = it.spaceCapacity,
            )
        }.toSet())

        return DeviceStorageScanTask.Result(itemCount = storages.size)
    }

    private suspend fun scanStorageContents(task: StorageScanTask): DeviceStorageScanTask.Result {
        log(TAG, VERBOSE) { "scanStorageContents(): $task" }

        // Inventory completeness is checked per-category inside StorageScanner so that media/system
        // scans still succeed when the app inventory is unavailable (e.g. Huawei TAF sandbox).

        val target = coreState.value.storages.singleOrNull { it.id == task.target }
            ?: throw IllegalStateException("Couldn't find ${task.target}")

        val scanner = storageScanner.get()

        val start = System.currentTimeMillis()

        val categories = scanner.withProgress(this) { scan(target) }

        val stop = System.currentTimeMillis()
        log(TAG) { "scanStorageContents() took ${stop - start}ms" }

        coreState.update { state ->
            state.copy(
                categories = state.categories.mutate {
                    this[target.id] = categories
                },
            )
        }

        return DeviceStorageScanTask.Result(itemCount = 0)
    }

    private suspend fun deleteContent(task: ContentDeleteTask): ContentDeleteTask.Result {
        log(TAG, VERBOSE) { "deleteContent(): $task" }

        val oldCategory: ContentCategory = coreState.value.categories[task.storageId]
            ?.singleOrNull { it.ownsGroup(task.groupId) }
            ?: throw IllegalStateException("Can't find category and group for ${task.groupId}")
        val oldGroup = oldCategory.groups.single { it.id == task.groupId }

        if (oldCategory.isContentReadOnly) {
            val what = when (oldCategory) {
                is SystemCategory -> "system content"
                is OtherUsersCategory -> "another user's content"
                else -> "read-only media content"
            }
            log(TAG, WARN) { "deleteContent(): Blocked — $what is read-only" }
            throw UnsupportedOperationException("Deletion is not supported for $what")
        }

        // App tasks are rebuilt against their pkgStat after deletion — resolve it now so a malformed task
        // (missing/wrong targetPkg, foreign group) fails before any file is deleted, not after.
        val oldPkg = (oldCategory as? AppCategory)?.let { category ->
            val pkg = category.pkgStats[task.targetPkg]
                ?: throw IllegalStateException("Can't find pkgStat ${task.targetPkg} for ${task.groupId}")
            val groups = setOfNotNull(pkg.appCode, pkg.appData, pkg.appMedia, pkg.extraData)
            if (groups.none { it == oldGroup }) {
                throw IllegalStateException("${pkg.id} has no content group matching ${task.groupId}")
            }
            pkg
        }

        updateProgressPrimary {
            it.getString(
                eu.darken.sdmse.common.R.string.general_progress_deleting_x,
                it.getQuantityString2(eu.darken.sdmse.common.R.plurals.result_x_items, task.targets.size)
            )
        }

        // Track what actually made it to the filesystem: a failure or cancellation part way through must
        // still publish the removals that already happened, otherwise the UI keeps listing deleted files
        // with stale sizes until the next manual refresh.
        val deletedTargets = mutableSetOf<APath>()
        var freedSpace = 0L
        try {
            task.targets
                .filterDistinctRoots()
                .forEach { target ->
                    log(TAG) { "Deleting $target" }
                    updateProgressSecondary(target.userReadablePath)
                    target.delete(gatewaySwitch, recursive = true)
                    deletedTargets.add(target)
                    (target as? LocalPath)?.let { mediaStoreTool.notifyDeleted(it) }
                }
        } finally {
            // NonCancellable: applyDeletion() suspends on the authoritative free-space re-read, which
            // would be skipped on the cancellation path. Costs a few binder calls before a cancelled
            // delete reports completion.
            if (deletedTargets.isNotEmpty()) withContext(NonCancellable) {
                freedSpace = applyDeletion(task, oldCategory, oldGroup, oldPkg, deletedTargets)
                mediaStoreTool.flush()
            }
        }

        return ContentDeleteTask.Result(
            affectedSpace = freedSpace,
            affectedPaths = task.targets,
        )
    }

    /**
     * Publishes the post-delete state for [deletedTargets] as one atomic update: shrunken content group,
     * refreshed device free space and a recomputed system residual. Returns the freed space that stats reports.
     */
    private suspend fun applyDeletion(
        task: ContentDeleteTask,
        oldCategory: ContentCategory,
        oldGroup: ContentGroup,
        oldPkg: AppCategory.PkgStat?,
        deletedTargets: Set<APath>,
    ): Long {
        var freedSpace = 0L
        val newContents = oldGroup.contents
            .toFlatContent()
            .filter { item ->
                val deleted = deletedTargets.any { it.isAncestorOf(item.path) || it.matches(item.path) }
                if (deleted) freedSpace += item.itemSize ?: 0L
                !deleted
            }
            .toNestedContent()

        val newGroup = oldGroup.copy(contents = newContents)

        val newCategory = when (oldCategory) {
            is AppCategory -> {
                checkNotNull(oldPkg) { "pkgStat was preflight-resolved for app categories" }
                val newPkg = when {
                    oldPkg.appCode == oldGroup -> oldPkg.copy(appCode = newGroup)
                    oldPkg.appData == oldGroup -> oldPkg.copy(appData = newGroup)
                    oldPkg.appMedia == oldGroup -> oldPkg.copy(appMedia = newGroup)
                    oldPkg.extraData == oldGroup -> oldPkg.copy(extraData = newGroup)
                    else -> error("unreachable: group membership was preflight-validated")
                }
                oldCategory.copy(
                    pkgStats = oldCategory.pkgStats.mutate {
                        this[oldPkg.id] = newPkg
                    }
                )
            }

            is MediaCategory -> oldCategory.copy(groups = oldCategory.groups.minus(oldGroup).plus(newGroup))

            is SystemCategory -> {
                throw UnsupportedOperationException("Deletion is not supported for system content")
            }

            is OtherUsersCategory -> {
                throw UnsupportedOperationException("Deletion is not supported for other users' content")
            }
        }

        // The authoritative numbers: what the filesystem reports as capacity and free space now. Called
        // plainly, not through withProgress(), so it doesn't disturb the delete's own progress.
        val refreshed: DeviceStorage? = try {
            deviceScanner.get().scan().firstOrNull { it.id == task.storageId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "applyDeletion(): free-space re-read failed, using accounted delta: ${e.asLog()}" }
            null
        }

        // Fallback uses the group-size delta, not the flat `freedSpace` sum, so the storage card moves by
        // exactly what the category card moved by. The two differ for entries whose ContentItem.size is null
        // but whose itemSize is not (symlinks and other non-file/dir types).
        val groupSizeDelta = oldGroup.groupSize - newGroup.groupSize

        coreState.update { state ->
            val newStorage = state.storages.singleOrNull { it.id == task.storageId }?.let { oldStorage ->
                when {
                    // spaceUsed is capacity minus free, so both have to come from the same measurement.
                    // DeviceStorageScanner can fall back from StorageStatsManager2 to the File API between
                    // two scans, and those measure different things (whole disk vs data partition) - pairing
                    // a refreshed free value with the cached capacity would move used space by gigabytes.
                    // setupIncomplete stays as cached: it says whether the published categories may be
                    // permission-limited, and a space re-read doesn't rebuild them. scanStorageDevices()
                    // owns that transition.
                    refreshed != null -> oldStorage.copy(
                        spaceCapacity = refreshed.spaceCapacity,
                        spaceFree = refreshed.spaceFree,
                    )

                    else -> {
                        // Clamped against remaining capacity headroom, so the addition can neither overflow
                        // Long nor push free space beyond the storage's capacity.
                        val headroom = (oldStorage.spaceCapacity - oldStorage.spaceFree).coerceAtLeast(0L)
                        oldStorage.copy(spaceFree = oldStorage.spaceFree + groupSizeDelta.coerceIn(0L, headroom))
                    }
                }
            }

            val updatedCategories = state.categories[task.storageId]!!.minus(oldCategory).plus(newCategory)

            // SystemCategory.spaceUsedOverride is a scan-time residual (used bytes minus apps/media/other
            // users), so it goes stale the moment the device's used bytes change.
            val finalCategories = when {
                newStorage == null -> updatedCategories
                else -> updatedCategories.map { category ->
                    if (category !is SystemCategory || category.spaceUsedOverride == null) return@map category
                    category.copy(
                        spaceUsedOverride = StorageScanner.computeResidual(
                            spaceUsed = newStorage.spaceUsed,
                            apps = updatedCategories.filterIsInstance<AppCategory>().sumOf { it.spaceUsed },
                            media = updatedCategories.filterIsInstance<MediaCategory>().sumOf { it.spaceUsed },
                            otherUsers = updatedCategories
                                .filterIsInstance<OtherUsersCategory>()
                                .sumOf { it.spaceUsed },
                        ),
                    )
                }
            }

            state.copy(
                storages = when {
                    // A delete never adds or removes storages, it only refreshes the target's free space.
                    newStorage == null -> state.storages
                    else -> state.storages.map { if (it.id == newStorage.id) newStorage else it }.toSet()
                },
                categories = state.categories.mutate {
                    this[task.storageId] = finalCategories
                },
            )
        }

        return freedSpace
    }

    private suspend fun deepScanApp(task: AppDeepScanTask): AppDeepScanTask.Result {
        log(TAG, VERBOSE) { "deepScanApp(): $task" }

        if (!appInventorySetupModule.isComplete()) {
            log(TAG, WARN) { "SetupModule INVENTORY is not complete" }
            throw IncompleteSetupException(SetupModule.Type.INVENTORY)
        }

        val targetStorage = coreState.value.storages.singleOrNull { it.id == task.storageId }
            ?: throw IllegalStateException("Couldn't find ${task.storageId}")
        val targetCategory = coreState.value.categories[targetStorage.id]!!.filterIsInstance<AppCategory>().single()
        val targetApp = targetCategory.pkgStats[task.installId]!!

        val start = System.currentTimeMillis()

        val updatedApp = withTimeoutOrNull(DEEP_SCAN_TIMEOUT) {
            storageScanner.get().withProgress(this@Analyzer) { deepScanApp(targetStorage, targetApp) }
        }

        val stop = System.currentTimeMillis()
        log(TAG) { "deepScanApp() took ${stop - start}ms" }

        if (updatedApp == null) {
            log(TAG, WARN) { "deepScanApp() timed out after ${stop - start}ms, keeping shallow data" }
            return AppDeepScanTask.Result(false)
        }

        coreState.update { state ->
            state.copy(
                categories = state.categories.mutate {
                    this[targetStorage.id] = this[targetStorage.id]!!.map { category ->
                        if (category !is AppCategory) return@map category
                        category.copy(pkgStats = category.pkgStats.mutate { replace(task.installId, updatedApp) })
                    }
                },
            )
        }

        return AppDeepScanTask.Result(true)
    }

    private suspend fun deepScanSystem(task: SystemDeepScanTask): SystemDeepScanTask.Result {
        log(TAG, VERBOSE) { "deepScanSystem(): $task" }

        val targetStorage = coreState.value.storages.singleOrNull { it.id == task.storageId }
            ?: throw IllegalStateException("Couldn't find ${task.storageId}")
        val existingCategory = coreState.value.categories[targetStorage.id]
            ?.filterIsInstance<SystemCategory>()
            ?.singleOrNull()
            ?: throw IllegalStateException("No SystemCategory for ${task.storageId}")

        val existingGroupId = existingCategory.groups.singleOrNull()?.id
            ?: throw IllegalStateException("No group in SystemCategory for ${task.storageId}")

        val start = System.currentTimeMillis()

        val updatedCategory = withTimeoutOrNull(DEEP_SCAN_TIMEOUT) {
            storageScanner.get().withProgress(this@Analyzer) {
                deepScanSystem(targetStorage, existingGroupId, existingCategory.spaceUsedOverride)
            }
        }

        val stop = System.currentTimeMillis()
        log(TAG) { "deepScanSystem() took ${stop - start}ms" }

        if (updatedCategory == null) {
            log(TAG, WARN) { "deepScanSystem() timed out after ${stop - start}ms, keeping existing data" }
            return SystemDeepScanTask.Result(false)
        }

        coreState.update { state ->
            state.copy(
                categories = state.categories.mutate {
                    this[targetStorage.id] = this[targetStorage.id]!!.map { category ->
                        if (category !is SystemCategory) return@map category
                        updatedCategory
                    }
                },
            )
        }

        return SystemDeepScanTask.Result(true)
    }

    internal data class CoreState(
        val storages: Set<DeviceStorage> = emptySet(),
        val categories: Map<StorageId, Collection<ContentCategory>> = emptyMap(),
    )

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
        private val DEEP_SCAN_TIMEOUT = 5.minutes
        private val TAG = logTag("Analyzer")
    }
}