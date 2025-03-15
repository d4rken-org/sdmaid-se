package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.automation.ClearCacheTask
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCacheProvider
import eu.darken.sdmse.automation.core.AutomationManager
import eu.darken.sdmse.automation.core.errors.AutomationUnavailableException
import eu.darken.sdmse.automation.core.errors.UserCancelledAutomationException
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
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
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
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
import kotlin.time.Duration.Companion.seconds


class InaccessibleDeleter @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val userManager: UserManager2,
    private val automationManager: AutomationManager,
    private val adbManager: AdbManager,
    private val pkgOps: PkgOps,
    private val inaccessibleCacheProvider: InaccessibleCacheProvider,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.Data(primary = eu.darken.sdmse.common.R.string.general_progress_preparing.toCaString())
    )
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun deleteInaccessible(
        snapshot: AppCleaner.Data,
        targetPkgs: Collection<InstallId>?,
        useAutomation: Boolean,
        isBackground: Boolean,
    ): InaccDelResult {
        log(TAG, INFO) { "deleteInaccessible() targetPkgs=${targetPkgs?.size}, $useAutomation" }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_preparing)
        updateProgressSecondary(CaString.EMPTY)
        updateProgressCount(Progress.Count.Indeterminate())

        val targetJunk = targetPkgs
            ?.mapNotNull { tp -> snapshot.junks.singleOrNull { it.identifier == tp } }
            ?: snapshot.junks

        val currentUser = userManager.currentUser()

        val targetInaccessible = targetJunk
            .filter { it.inaccessibleCache != null }
            .filter {
                // Without root, we shouldn't have inaccessible caches from other users
                val isCurrentUser = it.identifier.userHandle == currentUser.handle
                if (!isCurrentUser) {
                    log(TAG, WARN) { "Unexpected inaccessible data from other users: $it" }
                }
                isCurrentUser
            }
            .sortedByDescending { it.inaccessibleCache?.totalSize }

        return deleteInaccessible(
            targetInaccessible,
            isAllApps = targetPkgs == null,
            useAutomation = useAutomation,
            isBackground = isBackground,
        )
    }

    private suspend fun deleteInaccessible(
        targets: Collection<AppJunk>,
        isAllApps: Boolean,
        useAutomation: Boolean,
        isBackground: Boolean,
    ): InaccDelResult {
        log(TAG) { "${targets.size} inaccessible caches to delete." }
        if (targets.isEmpty()) return InaccDelResult()

        val successTargets = mutableListOf<InstallId>()
        val failedTargets = mutableListOf<InstallId>()

        if (adbManager.canUseAdbNow() && isAllApps) {
            val adbResult = trimCachesWithAdb(targets)
            successTargets.addAll(adbResult.succesful)
            failedTargets.addAll(adbResult.failed)
        }

        if (useAutomation && targets.size != successTargets.size) {
            log(TAG, WARN) { "Using accessibility service to delete inaccessible caches." }
            updateProgressPrimary(R.string.appcleaner_automation_loading)
            updateProgressSecondary(CaString.EMPTY)
            updateProgressCount(Progress.Count.Indeterminate())

            val remainingTargets = targets.filter { !successTargets.contains(it.identifier) }

            log(TAG) { "Processing ${remainingTargets.size} remaining inaccessible caches" }
            val successFullLive = mutableSetOf<InstallId>()
            val acsTask = ClearCacheTask(
                targets = remainingTargets.map { it.identifier },
                returnToApp = !isBackground,
                onSuccess = { successFullLive.add(it) }
            )
            val result = try {
                automationManager.submit(acsTask) as ClearCacheTask.Result
            } catch (e: AutomationUnavailableException) {
                throw InaccessibleDeletionException(e)
            } catch (e: UserCancelledAutomationException) {
                log(TAG, WARN) { "User has cancelled, forwarding live progress: $successFullLive" }
                ClearCacheTask.Result(
                    successful = successFullLive,
                    failed = emptySet(),
                )
            }

            successTargets.addAll(result.successful)
            failedTargets.addAll(result.failed)
        } else if (!useAutomation) {
            log(TAG, INFO) { "useAutomation=false" }
        }

        return InaccDelResult(
            succesful = successTargets.toSet(),
            failed = failedTargets.toSet(),
        )
    }

    private suspend fun trimCachesWithAdb(targets: Collection<AppJunk>): InaccDelResult {
        log(TAG) { "Using ADB to delete inaccessible caches" }
        updateProgressPrimary(R.string.appcleaner_progress_shizuku_deleting_caches)
        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_loading_app_data)

        val trimCandidates = targets.filter { !it.pkg.isSystemApp }
        updateProgressCount(Progress.Count.Counter(trimCandidates.size))

        val successTargets = mutableSetOf<InstallId>()
        val failedTargets = mutableSetOf<InstallId>()

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

                                newInfo.totalSize != beforeInfo.totalSize -> {
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

        return InaccDelResult(
            succesful = successTargets,
            failed = failedTargets
        )
    }

    data class InaccDelResult(
        val succesful: Set<InstallId> = emptySet(),
        val failed: Set<InstallId> = emptySet(),
    )

    companion object {
        private val TAG = logTag("AppCleaner", "Deleter", "Inaccessible")
    }
}
