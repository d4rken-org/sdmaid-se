package eu.darken.sdmse.appcleaner.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.automation.ClearCacheTask
import eu.darken.sdmse.appcleaner.core.scanner.AppScanner
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerDeleteTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerSchedulerTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask
import eu.darken.sdmse.automation.core.AutomationController
import eu.darken.sdmse.automation.core.errors.AutomationUnavailableException
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.*
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AppCleaner @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    fileForensics: FileForensics,
    private val gatewaySwitch: GatewaySwitch,
    pkgOps: PkgOps,
    private val appScannerProvider: Provider<AppScanner>,
    private val automationController: AutomationController,
    private val exclusionManager: ExclusionManager,
    private val userManager: UserManager2,
) : SDMTool, Progress.Client {

    private val usedResources = setOf(fileForensics, gatewaySwitch, pkgOps)

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    private val internalData = MutableStateFlow(null as Data?)
    val data: Flow<Data?> = internalData

    override val type: SDMTool.Type = SDMTool.Type.APPCLEANER

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
                        performDelete(AppCleanerDeleteTask(includeInaccessible = task.useAutomation))
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

        val deletionMap = mutableMapOf<Installed.InstallId, Set<APathLookup<*>>>()
        val snapshot = internalData.value ?: throw IllegalStateException("Data is null")

        val targetPkgs = task.targetPkgs ?: snapshot.junks.map { it.identifier }

        targetPkgs.forEach { targetPkg ->
            log(TAG) { "Processing $targetPkg" }
            if (task.onlyInaccessible) return@forEach

            val appJunk = snapshot.junks.single { it.identifier == targetPkg }
            updateProgressPrimary(appJunk.label)

            val targetFilters = task.targetFilters
                ?: appJunk.expendables?.keys
                ?: emptySet()

            val targetFiles: Collection<APathLookup<*>> = task.targetContents
                ?.map { tc ->
                    val allFiles = appJunk.expendables?.values?.flatten() ?: emptySet()
                    allFiles.single { tc.matches(it) }
                }
                ?: appJunk.expendables?.filterKeys { targetFilters.contains(it) }?.values?.flatten()
                ?: emptySet()

            val deleted = mutableSetOf<APathLookup<*>>()

            targetFiles.forEach { targetFile ->
                log(TAG) { "Deleting $targetFile..." }
                try {
                    targetFile.deleteAll(gatewaySwitch) {
                        updateProgressSecondary(it.userReadablePath)
                        true
                    }
                    log(TAG) { "Deleted $targetFile!" }
                    deleted.add(targetFile)
                } catch (e: WriteException) {
                    log(TAG, WARN) { "Deletion failed for $targetFile" }
                }
            }

            deletionMap[appJunk.identifier] = deleted
        }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)
        updateProgressSecondary(CaString.EMPTY)

        val currentUser = userManager.currentUser()
        val automationTargets = targetPkgs
            .filter { task.includeInaccessible }
            .filter { targetPkg ->
                snapshot.junks.single { it.identifier == targetPkg }.inaccessibleCache != null
            }
            .filter {
                // Without root, we shouldn't have inaccessible caches from other users
                val isCurrentUser = it.userHandle == currentUser.handle
                if (!isCurrentUser) {
                    log(TAG, WARN) { "Unexpected inaccessible data from other users: $it" }
                }
                isCurrentUser
            }

        val automationResult = automationTargets
            .takeIf { it.isNotEmpty() }
            ?.let {
                updateProgressPrimary(R.string.appcleaner_automation_loading)
                updateProgressSecondary(CaString.EMPTY)

                try {
                    automationController.submit(ClearCacheTask(automationTargets)) as ClearCacheTask.Result
                } catch (e: AutomationUnavailableException) {
                    throw InaccessibleDeletionException(e)
                }
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
                                val mapContent = deletionMap[appJunk.identifier]
                                mapContent?.none { it.matches(file) || it.isAncestorOf(file) } ?: true
                            }
                        }
                        ?.filterValues { it.isNotEmpty() }

                    val updatedInaccesible = when {
                        automationResult?.successful?.contains(appJunk.identifier) == true -> null
                        else -> appJunk.inaccessibleCache
                    }

                    appJunk.copy(
                        expendables = updatedExpendables,
                        inaccessibleCache = updatedInaccesible,
                    )
                }
                .filter { !it.isEmpty() }
        )

        val automationCount = automationResult?.successful
            ?.mapNotNull { installId -> snapshot.junks.single { it.identifier == installId }.inaccessibleCache?.itemCount }
            ?.sum() ?: 0
        val automationSize = automationResult?.successful
            ?.mapNotNull { installId -> snapshot.junks.single { it.identifier == installId }.inaccessibleCache?.cacheBytes }
            ?.sum() ?: 0L

        return AppCleanerDeleteTask.Success(
            deletedCount = deletionMap.values.sumOf { it.size } + automationCount,
            recoveredSpace = deletionMap.values.sumOf { contents -> contents.sumOf { it.size } } + automationSize,
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