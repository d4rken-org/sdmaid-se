package eu.darken.sdmse.appcleaner.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.scanner.AppScanner
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerOneClickTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerSchedulerTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.PathException
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.matches
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.progress.withProgress
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.setup.IncompleteSetupException
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.inventory.InventorySetupModule
import eu.darken.sdmse.setup.isComplete
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupModule
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
class AppCleaner @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    fileForensics: FileForensics,
    private val appScannerProvider: Provider<AppScanner>,
    private val inaccessibleDeleterProvider: Provider<InaccessibleDeleter>,
    private val exclusionManager: ExclusionManager,
    gatewaySwitch: GatewaySwitch,
    pkgOps: PkgOps,
    usageStatsSetupModule: UsageStatsSetupModule,
    rootManager: RootManager,
    shizukuManager: ShizukuManager,
    private val filterFactories: Set<@JvmSuppressWildcards ExpendablesFilter.Factory>,
    private val appInventorySetupModule: InventorySetupModule,
) : SDMTool, Progress.Client {

    private val usedResources = setOf(fileForensics, gatewaySwitch, pkgOps)

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val internalData = MutableStateFlow(null as Data?)

    override val type: SDMTool.Type = SDMTool.Type.APPCLEANER

    override val state: Flow<State> = combine(
        usageStatsSetupModule.state,
        rootManager.useRoot,
        shizukuManager.useShizuku,
        internalData,
        progress,
    ) { usageState, useRoot, useShizuku, data, progress ->
        State(
            data = data,
            progress = progress,
            isRunningAppsDetectionAvailable = usageState.isComplete || useRoot || useShizuku,
            isOtherUsersAvailable = useRoot,
            isInaccessibleCacheAvailable = usageState.isComplete || useRoot || useShizuku,
            isAcsRequired = !useRoot
        )
    }.replayingShare(appScope)

    private val toolLock = Mutex()
    override suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result = toolLock.withLock {
        task as AppCleanerTask
        log(TAG) { "submit(): Starting...$task" }
        updateProgress { Progress.Data() }
        try {
            val result = keepResourceHoldersAlive(usedResources) {
                when (task) {
                    is AppCleanerScanTask -> performScan(task)
                    is AppCleanerProcessingTask -> performProcessing(task)
                    is AppCleanerSchedulerTask -> {
                        performScan()
                        performProcessing(
                            AppCleanerProcessingTask(
                                useAutomation = task.useAutomation,
                                isBackground = true,
                            )
                        ).let {
                            AppCleanerSchedulerTask.Success(
                                affectedSpace = it.affectedSpace,
                                affectedPaths = it.affectedPaths,
                            )
                        }
                    }

                    is AppCleanerOneClickTask -> {
                        performScan()
                        performProcessing().let {
                            AppCleanerOneClickTask.Success(
                                affectedSpace = it.affectedSpace,
                                affectedPaths = it.affectedPaths,
                            )
                        }
                    }
                }
            }
            log(TAG, INFO) { "submit() finished: $result" }
            result
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun performScan(
        task: AppCleanerScanTask = AppCleanerScanTask()
    ): AppCleanerScanTask.Result {
        log(TAG, VERBOSE) { "performScan(): $task" }

        if (!appInventorySetupModule.isComplete()) {
            log(TAG, WARN) { "SetupModule INVENTORY is not complete" }
            throw IncompleteSetupException(SetupModule.Type.INVENTORY)
        }

        internalData.value = null

        val scanner = appScannerProvider.get()

        scanner.initialize()

        val results = scanner.withProgress(this) {
            scan()
        }

        log(TAG) { "Warming up fields..." }
        results.forEach { it.size }
        log(TAG) { "Field warm up done." }

        internalData.value = Data(
            junks = results,
        )

        return AppCleanerScanTask.Success(
            itemCount = results.size,
            recoverableSpace = results.sumOf { it.size },
        )
    }

    private suspend fun performProcessing(
        task: AppCleanerProcessingTask = AppCleanerProcessingTask()
    ): AppCleanerProcessingTask.Success {
        log(TAG, VERBOSE) { "performProcessing(): $task" }

        val snapshot = internalData.value ?: throw IllegalStateException("Data is null")

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_preparing)
        updateProgressSecondary(CaString.EMPTY)
        updateProgressCount(Progress.Count.Indeterminate())

        val filters = filterFactories
            .map { it.create() }
            .onEach { it.initialize() }

        val accessibleDeletionMap = if (!task.onlyInaccessible) {
            val deletionMap = mutableMapOf<Installed.InstallId, Set<ExpendablesFilter.Match>>()

            val targetJunk = task.targetPkgs
                ?.map { tp -> snapshot.junks.single { it.identifier == tp } }
                ?: snapshot.junks

            updateProgressCount(Progress.Count.Percent(targetJunk.size))

            targetJunk
                .sortedByDescending { it.size }
                .forEach { appJunk ->
                    updateProgressPrimary(appJunk.label)

                    val targetFilters = task.targetFilters
                        ?: appJunk.expendables?.keys
                        ?: emptySet()

                    val targetMatches: Collection<ExpendablesFilter.Match> = task.targetContents
                        ?.map { tc ->
                            val allFiles = appJunk.expendables?.values?.flatten() ?: emptySet()
                            allFiles.single { tc.matches(it.path) }
                        }
                        ?: appJunk.expendables?.filterKeys { targetFilters.contains(it) }?.values?.flatten()
                        ?: emptySet()

                    val deleted = mutableSetOf<ExpendablesFilter.Match>()

                    targetMatches.groupBy { it.identifier }.forEach { (filterIdentifier, matches) ->
                        val filter = filters.singleOrNull { it.identifier == filterIdentifier }
                            ?: throw IllegalStateException("Can't find filter for $filterIdentifier")

                        filter.withProgress(
                            client = this,
                            onUpdate = { old, new -> old?.copy(secondary = new?.secondary ?: CaString.EMPTY) },
                            onCompletion = { it }
                        ) {
                            try {
                                process(matches)
                                log(TAG) { "Processed ${matches.size} for $filterIdentifier at ${appJunk.pkg}!" }
                                deleted.addAll(matches)
                            } catch (e: PathException) {
                                log(TAG, ERROR) { "Deletion failed for $filterIdentifier at ${appJunk.pkg}: $e" }
                            }
                        }
                    }


                    deletionMap[appJunk.identifier] = deleted
                    increaseProgress()
                }

            deletionMap
        } else {
            emptyMap()
        }

        val inaccessibleSuccesses = if (task.includeInaccessible) {
            inaccessibleDeleterProvider.get().withProgress(
                this,
                onCompletion = { it }
            ) {
                deleteInaccessible(
                    snapshot = snapshot,
                    targetPkgs = task.targetPkgs,
                    useAutomation = task.useAutomation,
                    isBackground = task.isBackground
                ).succesful
            }
        } else {
            emptySet()
        }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_filtering)
        updateProgressSecondary(CaString.EMPTY)

        val deleted = mutableSetOf<APath>()

        internalData.value = snapshot.copy(
            junks = snapshot.junks
                .map { appJunk ->
                    updateProgressSecondary(appJunk.label)
                    // Remove all files we deleted or children of deleted files
                    val updatedExpendables = appJunk.expendables
                        ?.mapValues { (type, matches) ->
                            matches.filter { match ->
                                val isDeleted = accessibleDeletionMap.getOrDefault(appJunk.identifier, emptySet()).any {
                                    it.path.isAncestorOf(match.path) || it.path.matches(match.path)
                                }
                                if (isDeleted) deleted.add(match.path)
                                !isDeleted
                            }
                        }
                        ?.filterValues { it.isNotEmpty() }

                    val updatedInaccessible = when {
                        inaccessibleSuccesses.contains(appJunk.identifier) -> {
                            deleted.addAll(appJunk.inaccessibleCache?.theoreticalPaths!!)
                            null
                        }

                        else -> appJunk.inaccessibleCache
                    }

                    appJunk.copy(
                        expendables = updatedExpendables,
                        inaccessibleCache = updatedInaccessible,
                    )
                }
                .filter { !it.isEmpty() }
        )

        // Force check via !! because we should not have ran automation for any junk without inaccessible data
        val automationSize = inaccessibleSuccesses
            .map { inaccessible -> snapshot.junks.single { it.identifier == inaccessible }.inaccessibleCache!! }
            .sumOf { it.cacheBytes }

        return AppCleanerProcessingTask.Success(
            affectedSpace = accessibleDeletionMap.values.sumOf { contents -> contents.sumOf { it.expectedGain } } + automationSize,
            affectedPaths = deleted,
        )
    }

    suspend fun exclude(identifiers: Set<Installed.InstallId>) = toolLock.withLock {
        log(TAG) { "exclude(): $identifiers" }

        // FIXME what about user specific exclusion?
        val exclusions = identifiers.map {
            PkgExclusion(
                pkgId = it.pkgId,
                tags = setOf(Exclusion.Tag.APPCLEANER),
            )
        }.toSet()
        exclusionManager.save(exclusions)

        val snapshot = internalData.value!!
        internalData.value = snapshot.copy(
            junks = snapshot.junks.filter { junk ->
                exclusions.none { it.match(junk.identifier.pkgId) }
            }
        )
    }

    suspend fun exclude(identifier: Installed.InstallId, exclsionTargets: Set<APath>) = toolLock.withLock {
        log(TAG) { "exclude(): $identifier, $exclsionTargets" }
        val exclusions = exclsionTargets.map {
            PathExclusion(
                path = it,
                tags = setOf(Exclusion.Tag.APPCLEANER),
            )
        }.toSet()
        exclusionManager.save(exclusions)

        val snapshot = internalData.value!!
        internalData.value = snapshot.copy(
            junks = snapshot.junks.map { junk ->
                if (junk.identifier == identifier) {
                    junk.copy(
                        expendables = junk.expendables?.entries
                            ?.map { entry ->
                                entry.key to entry.value.filter { match ->
                                    val hit = exclusions.any { it.match(match.path) }
                                    if (hit) log(TAG) { "exclude(): Excluded $match" }
                                    !hit
                                }
                            }
                            ?.filter { it.second.isNotEmpty() }
                            ?.toMap()
                    )
                } else {
                    junk
                }
            }
        )
    }

    data class State(
        val data: Data?,
        val progress: Progress.Data?,
        val isOtherUsersAvailable: Boolean,
        val isRunningAppsDetectionAvailable: Boolean,
        val isInaccessibleCacheAvailable: Boolean,
        val isAcsRequired: Boolean,
    ) : SDMTool.State

    data class Data(
        val junks: Collection<AppJunk>
    ) {
        val totalSize: Long get() = junks.sumOf { it.size }
        val totalCount: Int get() = junks.sumOf { it.itemCount }
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: AppCleaner): SDMTool
    }

    companion object {
        private val TAG = logTag("AppCleaner")
    }
}