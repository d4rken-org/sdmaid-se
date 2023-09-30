package eu.darken.sdmse.appcleaner.core.deleter

import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.InaccessibleDeletionException
import eu.darken.sdmse.appcleaner.core.automation.ClearCacheTask
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCacheProvider
import eu.darken.sdmse.automation.core.AutomationManager
import eu.darken.sdmse.automation.core.errors.AutomationUnavailableException
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.PathException
import eu.darken.sdmse.common.files.deleteAll
import eu.darken.sdmse.common.files.filterDistinctRoots
import eu.darken.sdmse.common.files.matches
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.common.shizuku.canUseShizukuNow
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds


class AppJunkDeleter @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val gatewaySwitch: GatewaySwitch,
    private val userManager: UserManager2,
    private val automationManager: AutomationManager,
    private val shizukuManager: ShizukuManager,
    private val pkgOps: PkgOps,
    private val inaccessibleCacheProvider: InaccessibleCacheProvider,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.DEFAULT_STATE.copy(
            primary = eu.darken.sdmse.common.R.string.general_progress_preparing.toCaString()
        )
    )
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun initialize() {
        log(TAG, VERBOSE) { "initialize()" }
    }

    suspend fun deleteAccessible(
        snapshot: AppCleaner.Data,
        targetPkgs: Set<Installed.InstallId>?,
        targetFilters: Set<KClass<out ExpendablesFilter>>?,
        targetContents: Set<APath>?,
    ): Map<Installed.InstallId, Set<APathLookup<*>>> {
        log(TAG, INFO) {
            "deleteAccessible() pkgs=${targetPkgs?.size}, filters=${targetFilters?.size}, contents=${targetContents?.size}"
        }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_preparing)
        updateProgressSecondary(CaString.EMPTY)
        updateProgressCount(Progress.Count.Indeterminate())

        val deletionMap = mutableMapOf<Installed.InstallId, Set<APathLookup<*>>>()

        val targetJunk = targetPkgs
            ?.map { tp -> snapshot.junks.single { it.identifier == tp } }
            ?: snapshot.junks

        targetJunk
            .sortedByDescending { it.size }
            .forEach { appJunk ->
                val deleted = deleteDirectAccess(appJunk, targetFilters, targetContents)
                deletionMap[appJunk.identifier] = deleted
            }

        return deletionMap
    }

    private suspend fun deleteDirectAccess(
        appJunk: AppJunk,
        _targetFilters: Set<KClass<out ExpendablesFilter>>?,
        _targetContents: Set<APath>?,
    ): Set<APathLookup<*>> {
        log(TAG) { "Processing ${appJunk.identifier}" }

        updateProgressPrimary(appJunk.label)

        val targetFilters = _targetFilters
            ?: appJunk.expendables?.keys
            ?: emptySet()

        val targetFiles: Collection<APathLookup<*>> = _targetContents
            ?.map { tc ->
                val allFiles = appJunk.expendables?.values?.flatten() ?: emptySet()
                allFiles.single { tc.matches(it) }
            }
            ?: appJunk.expendables?.filterKeys { targetFilters.contains(it) }?.values?.flatten()
            ?: emptySet()

        val deleted = mutableSetOf<APathLookup<*>>()

        targetFiles
            .filterDistinctRoots()
            .forEach { targetFile ->
                log(TAG) { "Deleting $targetFile..." }
                updateProgressSecondary(targetFile.userReadablePath)
                try {
                    targetFile.deleteAll(gatewaySwitch)
                    log(TAG) { "Deleted $targetFile!" }
                    deleted.add(targetFile)
                } catch (e: PathException) {
                    log(TAG, WARN) { "Deletion failed for $targetFile" }
                }
            }

        return deleted
    }


    suspend fun deleteInaccessible(
        snapshot: AppCleaner.Data,
        targetPkgs: Collection<Installed.InstallId>?,
        useAutomation: Boolean,
    ): Set<Installed.InstallId> {
        log(TAG, INFO) {
            "deleteInaccessible() pkgs=${targetPkgs?.size}, $useAutomation"
        }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_preparing)
        updateProgressSecondary(CaString.EMPTY)
        updateProgressCount(Progress.Count.Indeterminate())

        val currentUser = userManager.currentUser()
        val targetInaccessible = targetPkgs
            ?.mapNotNull { tp ->
                snapshot.junks.singleOrNull { it.identifier == tp }
            } ?: snapshot.junks
            .filter { it.inaccessibleCache != null }
            .filter {
                // Without root, we shouldn't have inaccessible caches from other users
                val isCurrentUser = it.identifier.userHandle == currentUser.handle
                if (!isCurrentUser) {
                    log(TAG, WARN) { "Unexpected inaccessible data from other users: $it" }
                }
                isCurrentUser
            }
            .sortedByDescending { it.inaccessibleCache?.totalBytes }

        return deleteInaccessible(
            targetInaccessible,
            isAllApps = targetPkgs == null,
            useAutomation = useAutomation
        )
    }

    private suspend fun deleteInaccessible(
        targets: Collection<AppJunk>,
        isAllApps: Boolean,
        useAutomation: Boolean,
    ): Set<Installed.InstallId> {
        log(TAG) { "${targets.size} inaccessible caches to delete." }
        if (targets.isEmpty()) return emptySet()

        val successTargets = mutableListOf<Installed.InstallId>()
        val failedTargets = mutableListOf<Installed.InstallId>()

        if (shizukuManager.canUseShizukuNow() && isAllApps) {
            log(TAG) { "Using Shizuku to delete inaccessible caches" }
            updateProgressPrimary(R.string.appcleaner_progress_shizuku_deleting_caches)
            updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_loading_app_data)

            val trimCandidates = targets.filter { !it.pkg.isSystemApp }
            updateProgressCount(Progress.Count.Counter(trimCandidates.size))

            try {
                pkgOps.trimCaches(Long.MAX_VALUE)

                log(TAG) { "Waiting for trimCaches to take effect..." }
                delay(3000)

                val trimCacheResults = trimCandidates
                    .asFlow()
                    .flowOn(dispatcherProvider.IO)
                    .flatMapMerge { junk: AppJunk ->
                        val beforeInfo = junk.inaccessibleCache!!

                        suspend {
                            log(TAG) { "Observing status for ${junk.identifier}" }
                            var newInfo: InaccessibleCache? = null

                            while (currentCoroutineContext().isActive) {
                                newInfo = inaccessibleCacheProvider.determineCache(junk.pkg)
                                when {
                                    newInfo == null -> {
                                        log(TAG, WARN) { "Failed to query $beforeInfo" }
                                        break
                                    }

                                    newInfo.cacheBytes != beforeInfo.cacheBytes -> {
                                        log(TAG, VERBOSE) { "Size has changed $beforeInfo -> $newInfo" }
                                        break
                                    }

                                    else -> {
                                        log(TAG, VERBOSE) { "Size has not decreased yet for $newInfo" }
                                        delay(500L + 100 * (0..10).random())
                                    }
                                }
                            }

                            junk to newInfo
                        }.asFlow()
                    }
                    .onEach { increaseProgress() }
                    .timeout(10.seconds)
                    .catch { log(TAG, WARN) { "Size observations failed: $it" } }
                    .toList()

                log(TAG) { "Checking trimCaches result: $trimCacheResults" }
                updateProgressCount(Progress.Count.Indeterminate())

                trimCacheResults.forEach { (junk, result) ->
                    if (result != null) {
                        log(TAG) { "trimCache successful for ${junk.identifier}" }
                        successTargets.add(junk.identifier)
                    } else {
                        log(TAG, WARN) { "trimCache failed for ${junk.identifier}" }
                        failedTargets.add(junk.identifier)
                    }
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Trimming caches failed: ${e.asLog()}" }
                failedTargets.addAll(trimCandidates.map { it.identifier })
            }
        }

        if (useAutomation && targets.size != successTargets.size) {
            log(TAG, WARN) { "Using accessibility service to delete inaccessible caches." }
            updateProgressPrimary(R.string.appcleaner_automation_loading)
            updateProgressSecondary(CaString.EMPTY)
            updateProgressCount(Progress.Count.Indeterminate())

            val remainingTargets = targets.filter { !successTargets.contains(it.identifier) }

            log(TAG) { "Processing ${remainingTargets.size} remaining inaccessible caches" }
            val acsTask = ClearCacheTask(remainingTargets.map { it.identifier })
            val result = try {
                automationManager.submit(acsTask) as ClearCacheTask.Result
            } catch (e: AutomationUnavailableException) {
                throw InaccessibleDeletionException(e)
            }

            successTargets.addAll(result.successful)
        } else if (!useAutomation) {
            log(TAG, INFO) { "useAutomation=false" }
        }

        return successTargets.toSet()
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Deleter")
    }
}
