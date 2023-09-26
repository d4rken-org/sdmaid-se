package eu.darken.sdmse.appcleaner.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.core.deleter.AppJunkDeleter
import eu.darken.sdmse.appcleaner.core.scanner.AppScanner
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerDeleteTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerSchedulerTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.main.core.SDMTool
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
    private val appJunkDeleterProvider: Provider<AppJunkDeleter>,
    private val exclusionManager: ExclusionManager,
    gatewaySwitch: GatewaySwitch,
    pkgOps: PkgOps,
    usageStatsSetupModule: UsageStatsSetupModule,
    rootManager: RootManager,
    shizukuManager: ShizukuManager,
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
        updateProgress { Progress.DEFAULT_STATE }
        try {
            val result = keepResourceHoldersAlive(usedResources) {
                when (task) {
                    is AppCleanerScanTask -> performScan(task)
                    is AppCleanerDeleteTask -> performDelete(task)
                    is AppCleanerSchedulerTask -> {
                        performScan(AppCleanerScanTask())
                        performDelete(AppCleanerDeleteTask(useAutomation = task.useAutomation))
                    }
                }
            }
            log(TAG, INFO) { "submit() finished: $result" }
            result
        } finally {
            updateProgress { null }
        }
    }

    private suspend fun performScan(task: AppCleanerScanTask): AppCleanerScanTask.Result {
        log(TAG, VERBOSE) { "performScan(): $task" }

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

    private suspend fun performDelete(task: AppCleanerDeleteTask): AppCleanerDeleteTask.Result {
        log(TAG, VERBOSE) { "performDelete(): $task" }

        val snapshot = internalData.value ?: throw IllegalStateException("Data is null")

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)
        updateProgressSecondary(CaString.EMPTY)

        val deleter = appJunkDeleterProvider.get()

        deleter.initialize()

        val accessibleDeletionMap = if (!task.onlyInaccessible) {
            deleter.withProgress(this) {
                deleter.deleteAccessible(
                    snapshot = snapshot,
                    targetPkgs = task.targetPkgs,
                    targetFilters = task.targetFilters,
                    targetContents = task.targetContents,
                )
            }
        } else {
            emptyMap()
        }

        val inaccessibleSuccesses = if (task.includeInaccessible) {
            deleter.withProgress(this) {
                deleter.deleteInaccessible(
                    snapshot = snapshot,
                    targetPkgs = task.targetPkgs,
                    useAutomation = task.useAutomation,
                )
            }
        } else {
            emptySet()
        }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_filtering)
        updateProgressSecondary(CaString.EMPTY)

        internalData.value = snapshot.copy(
            junks = snapshot.junks
                .map { appJunk ->
                    updateProgressSecondary(appJunk.label)
                    // Remove all files we deleted or children of deleted files
                    val updatedExpendables = appJunk.expendables
                        ?.mapValues { (type, typeFiles) ->
                            typeFiles.filter { file ->
                                val isDeleted = accessibleDeletionMap.getOrDefault(appJunk.identifier, emptySet()).any {
                                    it.isAncestorOf(file) || it.matches(file)
                                }
                                !isDeleted
                            }
                        }
                        ?.filterValues { it.isNotEmpty() }

                    val updatedInaccessible = when {
                        inaccessibleSuccesses.contains(appJunk.identifier) -> null
                        else -> appJunk.inaccessibleCache
                    }

                    appJunk.copy(
                        expendables = updatedExpendables,
                        inaccessibleCache = updatedInaccessible,
                    )
                }
                .filter { !it.isEmpty() }
        )

        val automationCount = inaccessibleSuccesses
            .map { inaccessible -> snapshot.junks.single { it.identifier == inaccessible }.inaccessibleCache!! }
            .sumOf { it.itemCount }
        val automationSize = inaccessibleSuccesses
            .map { inaccessible -> snapshot.junks.single { it.identifier == inaccessible }.inaccessibleCache!! }
            .sumOf { it.cacheBytes }

        return AppCleanerDeleteTask.Success(
            deletedCount = accessibleDeletionMap.values.sumOf { it.size } + automationCount,
            recoveredSpace = accessibleDeletionMap.values.sumOf { contents -> contents.sumOf { it.size } } + automationSize,
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
                                entry.key to entry.value.filter { junkFile ->
                                    val hit = exclusions.any { it.match(junkFile.lookedUp) }
                                    if (hit) log(TAG) { "exclude(): Excluded $junkFile" }
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