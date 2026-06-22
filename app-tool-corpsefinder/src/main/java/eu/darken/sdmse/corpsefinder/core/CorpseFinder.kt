package eu.darken.sdmse.corpsefinder.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.WriteException
import eu.darken.sdmse.common.files.delete
import eu.darken.sdmse.common.files.filterDistinctRoots
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.matches
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.corpsefinder.core.filter.CorpseFilter
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderOneClickTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderSchedulerTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderTask
import eu.darken.sdmse.corpsefinder.core.tasks.UninstallWatcherTask
import eu.darken.sdmse.corpsefinder.core.watcher.ExternalWatcherResult
import eu.darken.sdmse.corpsefinder.core.watcher.WatcherNotifications
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.pathExclusions
import eu.darken.sdmse.exclusion.core.pkgExclusions
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.match
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.setup.SetupHeartbeat
import eu.darken.sdmse.setup.SetupBinding
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorpseFinder @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val filterFactories: Set<@JvmSuppressWildcards CorpseFilter.Factory>,
    private val fileForensics: FileForensics,
    private val gatewaySwitch: GatewaySwitch,
    private val exclusionManager: ExclusionManager,
    private val userManager: UserManager2,
    private val pkgOps: PkgOps,
    private val watcherNotifications: WatcherNotifications,
    rootManager: RootManager,
    settings: CorpseFinderSettings,
    @SetupBinding(SetupModule.Type.INVENTORY) private val inventorySetupCheck: SetupHeartbeat,
) : SDMTool, Progress.Client {

    override val type: SDMTool.Type = SDMTool.Type.CORPSEFINDER

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val internalData = MutableStateFlow(null as Data?)

    override val state: Flow<State> = combine(
        internalData,
        progress,
        rootManager.useRoot,
        settings.isWatcherEnabled.flow
    ) { data, progress, useRoot, isWatcherEnabled ->
        State(
            data = data,
            progress = progress,
            isFilterPrivateDataAvailable = useRoot,
            isFilterDalvikCacheAvailable = useRoot,
            isFilterArtProfilesAvailable = useRoot,
            isFilterAppLibrariesAvailable = useRoot,
            isFilterAppSourcesAvailable = useRoot,
            isFilterPrivateAppSourcesAvailable = useRoot,
            isFilterEncryptedAppResourcesAvailable = useRoot,
        )
    }.replayingShare(appScope)

    private val toolLock = Mutex()

    /** Drops the current scan results (and only those — progress, settings and task history are unaffected). */
    suspend fun discardScanData() = toolLock.withLock {
        log(TAG) { "discardScanData()" }
        internalData.value = null
    }

    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = toolLock.withLock {
        task as CorpseFinderTask
        log(TAG, INFO) { "submit($task) starting..." }
        updateProgress { Progress.Data() }

        try {
            val result = keepResourceHoldersAlive(fileForensics, gatewaySwitch, pkgOps) {
                when (task) {
                    is CorpseFinderScanTask -> performScan(task)
                    is CorpseFinderDeleteTask -> deleteCorpses(task)
                    is UninstallWatcherTask -> {
                        // Watcher uses runScan + deleteCorpses(snapshot=...) so it never
                        // mutates internalData wholesale. The user's open CorpseFinder view —
                        // including a non-empty list of unrelated corpses — survives the
                        // background event. The reconciliation block below handles the
                        // autoDelete case by removing only the deleted paths from the user's
                        // visible state.
                        val watcherCorpses = runScan(
                            CorpseFinderScanTask(pkgIdFilter = setOf(task.target))
                        )
                        val targets = watcherCorpses
                            .map { it.identifier }
                            .onEach { log(TAG) { "Uninstall watcher found target $it" } }
                            .toSet()

                        log(TAG) { "Watcher auto delete enabled=${task.autoDelete}" }

                        val internalDeleteResult = if (task.autoDelete && targets.isNotEmpty()) {
                            val watcherSnapshot = Data(corpses = watcherCorpses)
                            val deleteResult = deleteCorpses(
                                task = CorpseFinderDeleteTask(targetCorpses = targets),
                                snapshot = watcherSnapshot,
                            )

                            // Reconcile: remove any deleted paths from the user's visible
                            // internalData if they happened to be present there. If the user
                            // had no scan results (internalData == null) we leave it alone —
                            // the watcher isn't a substitute for a user-driven scan.
                            internalData.value?.let { userVisible ->
                                val survivors = userVisible.corpses.filterNot {
                                    it.lookup.lookedUp in deleteResult.affectedPaths
                                }
                                internalData.value = userVisible.copy(corpses = survivors)
                            }

                            val watcherResult = ExternalWatcherResult.Deletion(
                                appName = pkgOps.getLabel(task.target)?.toCaString(),
                                pkgId = task.target,
                                deletedItems = deleteResult.affectedCount,
                                freedSpace = deleteResult.affectedSpace,
                            )
                            watcherNotifications.notifyOfDeletion(watcherResult)
                            deleteResult
                        } else {
                            if (targets.isNotEmpty()) {
                                val watcherResult = ExternalWatcherResult.Scan(
                                    pkgId = task.target,
                                    foundItems = targets.size
                                )
                                watcherNotifications.notifyOfScan(watcherResult)
                            }
                            null
                        }

                        UninstallWatcherTask.Success(
                            foundItems = targets.size,
                            affectedPaths = internalDeleteResult?.affectedPaths ?: emptySet(),
                            affectedSpace = internalDeleteResult?.affectedSpace ?: 0L,
                        )
                    }

                    is CorpseFinderSchedulerTask -> {
                        performScan()
                        deleteCorpses().let {
                            CorpseFinderSchedulerTask.Success(
                                affectedSpace = it.affectedSpace,
                                affectedPaths = it.affectedPaths,
                            )
                        }
                    }

                    is CorpseFinderOneClickTask -> {
                        performScan()
                        deleteCorpses().let {
                            CorpseFinderOneClickTask.Success(
                                affectedSpace = it.affectedSpace,
                                affectedPaths = it.affectedPaths,
                            )
                        }
                    }
                }
            }
            // `lastResult` advertises the user-visible "last action" — UninstallWatcherTask is
            // a background event and must not overwrite that field. Otherwise the dashboard
            // would replace the user's "X corpses found" / "X items deleted" with the watcher's
            // success info as if the watcher were the user's last action.
            if (task !is UninstallWatcherTask) {
                internalData.value = internalData.value?.copy(
                    lastResult = result,
                )
            }
            log(TAG, INFO) { "submit($task) finished: $result" }
            result
        } catch (e: CancellationException) {
            throw e
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun performScan(
        task: CorpseFinderScanTask = CorpseFinderScanTask()
    ): CorpseFinderScanTask.Result {
        log(TAG) { "performScan(): $task" }
        // User-driven scan path: clears the user-visible state, runs the scan, publishes the
        // result. The watcher must NOT take this path — it uses `runScan` directly so
        // background events don't disturb the user's open CorpseFinder view.
        internalData.value = null
        val results = runScan(task)
        internalData.value = Data(corpses = results)
        return CorpseFinderScanTask.Success(
            itemCount = results.size,
            recoverableSpace = results.sumOf { it.size },
        )
    }

    /**
     * Pure scan: runs all enabled filter factories, applies exclusions / multi-user / pkgIdFilter,
     * and returns the resulting corpses. Does NOT mutate [internalData]. The caller decides
     * whether to publish the result, ignore it, or merge it into existing state.
     */
    private suspend fun runScan(task: CorpseFinderScanTask): List<Corpse> {
        log(TAG) { "runScan(): $task" }

        inventorySetupCheck.checkOrThrow()

        val filters = filterFactories
            .filter { it.isEnabled() }
            .map { it.create() }
            .onEach { log(TAG) { "Created filter: $it" } }

        val pathExclusions = exclusionManager.pathExclusions(SDMTool.Type.CORPSEFINDER)
        val pkgExclusions = exclusionManager.pkgExclusions(SDMTool.Type.CORPSEFINDER)

        val results = filters
            .map { filter ->
                filter
                    .withProgress(this@CorpseFinder) { scan() }
                    .also { log(TAG) { "$filter found ${it.size} corpses" } }
            }
            .flatten()
            .filter { corpse ->
                pkgExclusions.none { excl ->
                    corpse.ownerInfo.owners.any { owner ->
                        excl.match(owner.pkgId).also {
                            if (it) log(TAG, INFO) { "Excluded due to $excl: $corpse" }
                        }
                    }
                }
            }
            .filter { corpse ->
                pathExclusions.none { excl ->
                    excl.match(corpse.lookup).also {
                        if (it) log(TAG, INFO) { "Excluded due to $excl: $corpse" }
                    }
                }
            }
            .filter { corpse ->
                // One extra check for multi-user devices without root
                if (!userManager.hasMultiUserSupport) return@filter true

                // Another user might own corpses in secondary public storage
                if (corpse.areaInfo.userHandle != userManager.systemUser().handle) return@filter true

                if (corpse.ownerInfo.owners.any { pkgOps.isInstalleMaybe(it.pkgId, it.userHandle) }) {
                    log(TAG, WARN) { "Potential multi-user false positive: $corpse" }
                    return@filter false
                }

                return@filter true
            }
            .filter { corpse ->
                // Honour task.pkgIdFilter — corpses whose owners include any of the target
                // packages are kept; everything else is discarded. Empty filter means "no
                // filter" (legacy: all corpses pass). Behaviour note: this is INCLUSIVE on
                // multi-owner corpses (kept if ANY owner matches), which differs from the
                // legacy watcher path that used `ownerInfo.getOwner(target)` (singleOrNull) —
                // multi-owner corpses where the target package was just one of several owners
                // are now correctly kept.
                if (task.pkgIdFilter.isEmpty()) return@filter true
                corpse.ownerInfo.owners.any { it.pkgId in task.pkgIdFilter }
            }

        results.forEach { log(TAG, INFO) { "Result: $it" } }

        log(TAG) { "Warming up fields..." }
        results.forEach { it.size }
        log(TAG) { "Field warm up done." }

        return results
    }

    /**
     * Delete corpses (or specific content paths within corpses) from disk and update state.
     *
     * [snapshot] is the [Data] view to look up identifiers against. Pass `null` (default) to
     * use [internalData] — the standard user-driven path that also publishes the updated
     * state back to the flow. Pass an explicit snapshot when operating on a side-channel set
     * of corpses (e.g. the uninstall watcher, which operates on a filtered scan result it
     * never wants to expose to the user via [internalData]). When [snapshot] is non-null the
     * caller is responsible for reconciling [internalData] with the deletion outcome.
     */
    private suspend fun deleteCorpses(
        task: CorpseFinderDeleteTask = CorpseFinderDeleteTask(),
        snapshot: Data? = null,
    ): CorpseFinderDeleteTask.Success {
        log(TAG) { "deleteCorpses(): $task (externalSnapshot=${snapshot != null})" }

        val deletedCorpses = mutableSetOf<Corpse>()
        val deletedContents = mutableMapOf<Corpse, Set<APath>>()
        val usingExternalSnapshot = snapshot != null
        val activeSnapshot = snapshot
            ?: internalData.value
            ?: throw IllegalStateException("Data is null")

        val targetCorpses = task.targetCorpses ?: activeSnapshot.corpses.map { it.identifier }
        targetCorpses.forEach { targetCorpse ->
            val corpse = activeSnapshot.corpses.single { it.identifier == targetCorpse }

            if (!task.targetContent.isNullOrEmpty()) {
                // Per-corpse safety filter: only attempt to delete paths that this corpse
                // actually owns. The predicate mirrors the post-delete state-update at the
                // bottom of this method — `targetContent` path P "belongs to" the corpse if it
                // matches one of corpse.content directly OR is an ancestor of any such item
                // (so a directory selection still recursively covers its child entries).
                //
                // filterDistinctRoots() runs AFTER this per-corpse filter so that a directory
                // path picked for corpse A doesn't collapse a separately-selected file path
                // owned by corpse B.
                val ownedTargets = task.targetContent
                    .filter { candidate ->
                        corpse.content.any { contentItem ->
                            candidate.isAncestorOf(contentItem) || candidate.matches(contentItem)
                        }
                    }
                    .filterDistinctRoots()

                val deleted = mutableSetOf<APath>()
                ownedTargets.forEach { targetContent ->
                    updateProgressPrimary(caString {
                        it.getString(
                            eu.darken.sdmse.common.R.string.general_progress_deleting_x,
                            targetContent.userReadableName.get(it)
                        )
                    })
                    log(TAG) { "Deleting $targetContent..." }
                    updateProgressSecondary(targetContent.userReadablePath)
                    try {
                        targetContent.delete(gatewaySwitch, recursive = true)
                        log(TAG) { "Deleted $targetContent!" }
                        deleted.add(targetContent)
                    } catch (e: WriteException) {
                        log(TAG, WARN) { "Deletion failed for $targetContent: $e" }
                    }
                }

                deletedContents[corpse] = deleted
            } else {
                updateProgressPrimary(caString {
                    it.getString(
                        eu.darken.sdmse.common.R.string.general_progress_deleting_x,
                        corpse.lookup.userReadableName.get(it)
                    )
                })
                log(TAG) { "Deleting $targetCorpse..." }
                updateProgressSecondary(corpse.lookup.userReadablePath)
                try {
                    corpse.lookup.delete(gatewaySwitch, recursive = true)
                    log(TAG) { "Deleted $targetCorpse!" }
                    deletedCorpses.add(corpse)
                } catch (e: WriteException) {
                    log(TAG, WARN) { "Deletion failed for $targetCorpse: $e" }
                }
            }
        }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)
        updateProgressSecondary(CaString.EMPTY)

        var deletedContentSize = 0L

        // Compute the updated snapshot (corpses minus the deleted ones, content trimmed).
        // affectedSpace counts the side-effect on disk regardless of whether the snapshot was
        // ours-to-publish.
        val updatedCorpses = activeSnapshot.corpses
            .mapNotNull { corpse ->
                when {
                    deletedCorpses.contains(corpse) -> null
                    deletedContents.containsKey(corpse) -> corpse.copy(
                        content = corpse.content.filter { contentItem ->
                            val isDeleted = deletedContents[corpse]!!.any { deleted ->
                                deleted.isAncestorOf(contentItem) || deleted.matches(contentItem)
                            }
                            if (isDeleted) deletedContentSize += contentItem.size
                            !isDeleted
                        }
                    )

                    else -> corpse
                }
            }

        // Only publish back to internalData when we sourced FROM internalData; with an external
        // snapshot the caller owns the reconciliation (e.g. the watcher needs to merge the
        // deletion into the user's full corpse list, not replace it).
        if (!usingExternalSnapshot) {
            internalData.value = activeSnapshot.copy(corpses = updatedCorpses)
        }

        return CorpseFinderDeleteTask.Success(
            affectedSpace = deletedCorpses.sumOf { it.size } + deletedContentSize,
            affectedPaths = deletedCorpses.map { it.lookup.lookedUp }.toSet() + deletedContents.flatMap { it.value },
        )
    }

    suspend fun exclude(identifiers: Set<CorpseIdentifier>): ExclusionUndo = toolLock.withLock {
        log(TAG) { "exclude(): $identifiers" }

        val snapshot = internalData.value!!

        val targets = snapshot.corpses
            .filter { identifiers.contains(it.identifier) }

        val exclusions = targets.map {
            PathExclusion(
                path = it.lookup.lookedUp,
                tags = setOf(Exclusion.Tag.CORPSEFINDER),
            )
        }.toSet()
        val saved = exclusionManager.save(exclusions)

        val updated = snapshot.copy(
            corpses = snapshot.corpses.filter { corpse ->
                exclusions.none { it.match(corpse.lookup) }
            }
        )
        internalData.value = updated

        ExclusionUndo(
            exclusionIds = saved.map { it.id }.toSet(),
            previousData = snapshot,
            postExcludeData = updated,
        )
    }

    suspend fun undoExclude(handle: ExclusionUndo) = toolLock.withLock {
        log(TAG, INFO) { "undoExclude(${handle.exclusionIds})" }
        if (handle.exclusionIds.isNotEmpty()) {
            exclusionManager.remove(handle.exclusionIds)
        }
        if (internalData.value === handle.postExcludeData) {
            internalData.value = handle.previousData
        } else {
            log(TAG, WARN) { "undoExclude: state moved on, only removed exclusions" }
        }
    }

    data class ExclusionUndo(
        val exclusionIds: Set<ExclusionId>,
        internal val previousData: Data,
        internal val postExcludeData: Data,
    )

    data class State(
        val data: Data?,
        val progress: Progress.Data?,
        val isFilterPrivateDataAvailable: Boolean,
        val isFilterDalvikCacheAvailable: Boolean,
        val isFilterArtProfilesAvailable: Boolean,
        val isFilterAppLibrariesAvailable: Boolean,
        val isFilterAppSourcesAvailable: Boolean,
        val isFilterPrivateAppSourcesAvailable: Boolean,
        val isFilterEncryptedAppResourcesAvailable: Boolean,
    ) : SDMTool.State

    data class Data(
        val corpses: Collection<Corpse>,
        val lastResult: CorpseFinderTask.Result? = null,
    ) {
        val totalSize: Long get() = corpses.sumOf { it.size }
        val totalCount: Int get() = corpses.size
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: CorpseFinder): SDMTool
    }

    companion object {
        private val TAG = logTag("CorpseFinder")
    }
}