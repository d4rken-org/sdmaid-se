package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.R
import eu.darken.sdmse.appcleaner.core.automation.ClearCacheTask
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCacheProvider
import eu.darken.sdmse.automation.core.AutomationSubmitter
import eu.darken.sdmse.automation.core.ForceStopAutomationTask
import eu.darken.sdmse.automation.core.errors.AutomationUnavailableException
import eu.darken.sdmse.automation.core.errors.DisabledAppException
import eu.darken.sdmse.automation.core.errors.NoSettingsWindowException
import eu.darken.sdmse.automation.core.errors.UserCancelledAutomationException
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.pkgs.NoSettingsDetector
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.setup.SetupBinding
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.isComplete
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class InaccessibleDeleter @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val userManager: UserManager2,
    private val automationManager: AutomationSubmitter,
    private val adbManager: AdbManager,
    private val pkgOps: PkgOps,
    private val inaccessibleCacheProvider: InaccessibleCacheProvider,
    private val rootManager: RootManager,
    private val settings: AppCleanerSettings,
    @SetupBinding(SetupModule.Type.AUTOMATION) private val automationSetupModule: SetupModule,
    private val noSettingsDetector: NoSettingsDetector,
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
        val failedTargets = mutableMapOf<InstallId, Exception>()

        if (adbManager.canUseAdbNow() && isAllApps) {
            val adbResult = trimCachesWithAdb(targets)
            successTargets.addAll(adbResult.succesful)
            failedTargets.putAll(adbResult.failed)
        }

        val remainingTargets = targets
            .filter { !successTargets.contains(it.identifier) }
            .filter { junk ->
                val currentCache = inaccessibleCacheProvider.determineCache(junk.pkg)
                if (currentCache != null && currentCache.totalSize == 0L) {
                    log(TAG) { "Cache now zero, skipping automation: ${junk.identifier}" }
                    successTargets.add(junk.identifier)
                    false
                } else {
                    true
                }
            }

        if (useAutomation && remainingTargets.isNotEmpty()) {
            log(TAG, WARN) { "Using accessibility service to delete inaccessible caches." }
            updateProgressPrimary(eu.darken.sdmse.automation.R.string.automation_loading)
            updateProgressSecondary(CaString.EMPTY)
            updateProgressCount(Progress.Count.Indeterminate())

            // Pre-flight: packages whose settings page we know we can't reach can never be cleared
            // via ACS. Mark them as failed upfront so we don't waste ~4-10s per package discovering
            // this reactively inside the automation flow.
            val automationCandidates = remainingTargets.filter { junk ->
                val reason = noSettingsDetector.getUnreachableReason(junk.pkg)
                if (reason != null) {
                    log(TAG, INFO) { "Pre-flight: unreachable ($reason), marking failed: ${junk.identifier}" }
                    val pkgName = junk.identifier.pkgId.name
                    failedTargets[junk.identifier] = when (reason) {
                        NoSettingsDetector.Reason.NO_SETTINGS_PAGE -> NoSettingsWindowException(
                            "$pkgName has no settings window (pre-flight)."
                        )

                        NoSettingsDetector.Reason.DISABLED_APP -> DisabledAppException(
                            "$pkgName is disabled, its settings window can't be opened (pre-flight)."
                        )
                    }
                }
                reason == null
            }

            log(TAG) { "Processing ${automationCandidates.size} remaining inaccessible caches" }
            automationCandidates.forEach { log(TAG, VERBOSE) { "Remaining ACS target: $it" } }

            if (automationCandidates.isEmpty()) {
                log(TAG) { "All remaining targets pre-flight failed, nothing to submit to ACS" }
            } else {
                // Force-stop apps before clearing cache if enabled
                if (settings.forceStopBeforeClearing.value()) {
                    try {
                        forceStopApps(automationCandidates.map { it.identifier })
                    } catch (e: UserCancelledAutomationException) {
                        log(TAG, WARN) { "User cancelled during force-stop; skipping cache clear." }
                        // Honor the cancel as a full stop: don't proceed to clear cache.
                        successTargets.forEach { failedTargets.remove(it) }
                        return InaccDelResult(
                            succesful = successTargets.toSet(),
                            failed = failedTargets,
                        )
                    }
                }

                val successLive = mutableSetOf<InstallId>()
                val failedLive = mutableMapOf<InstallId, Exception>()

                val acsTask = ClearCacheTask(
                    targets = automationCandidates.map { it.identifier },
                    returnToApp = !isBackground,
                    onSuccess = { successLive.add(it) },
                    onError = { id, error -> failedLive[id] = error }
                )
                val result = try {
                    automationManager.submit(acsTask) as ClearCacheTask.Result
                } catch (e: AutomationUnavailableException) {
                    throw InaccessibleDeletionException(e)
                } catch (e: UserCancelledAutomationException) {
                    log(TAG, WARN) { "User has cancelled ($e), forwarding live progress: $successLive" }
                    ClearCacheTask.Result(
                        successful = successLive,
                        failed = failedLive,
                    )
                }

                successTargets.addAll(result.successful)
                failedTargets.putAll(result.failed)
            }
        } else if (!useAutomation) {
            log(TAG, INFO) { "useAutomation=false" }
        }

        // Clean up contradictory bookkeeping: if an app failed earlier but succeeded later, remove from failed
        successTargets.forEach { failedTargets.remove(it) }

        return InaccDelResult(
            succesful = successTargets.toSet(),
            failed = failedTargets,
        )
    }

    private suspend fun forceStopApps(rawTargets: List<InstallId>) {
        val targets = rawTargets.filter { target ->
            val offLimits = ForceStopAutomationTask.OFF_LIMIT_PKGS.contains(target.pkgId)
            if (offLimits) log(TAG, WARN) { "Skipping $target: force-stopping it would break accessibility automation" }
            !offLimits
        }
        if (targets.isEmpty()) {
            log(TAG) { "No force-stoppable apps in ${rawTargets.size} targets" }
            return
        }
        log(TAG, INFO) { "Force-stopping ${targets.size} apps before clearing cache" }

        updateProgressPrimary(eu.darken.sdmse.appcleaner.R.string.appcleaner_progress_force_stopping)
        updateProgressSecondary(CaString.EMPTY)

        if (rootManager.canUseRootNow() || adbManager.canUseAdbNow()) {
            updateProgressCount(Progress.Count.Percent(targets.size))
            log(TAG) { "Using ROOT/ADB for force-stop" }
            targets.forEach { installId ->
                try {
                    pkgOps.forceStop(installId)
                    log(TAG, VERBOSE) { "Force-stopped $installId" }
                } catch (e: Exception) {
                    // Don't swallow cancellation of our own scope (e.g. user cancelled the task).
                    currentCoroutineContext().ensureActive()
                    log(TAG, WARN) { "Failed to force-stop $installId: ${e.asLog()}" }
                } finally {
                    increaseProgress()
                }
            }
        } else if (automationSetupModule.isComplete()) {
            updateProgressCount(Progress.Count.Indeterminate())
            log(TAG) { "Using Automation for force-stop" }
            val task = ForceStopAutomationTask(targets)
            try {
                automationManager.submit(task)
            } catch (e: UserCancelledAutomationException) {
                // User cancelled => full stop. Propagate so the caller aborts before clearing cache.
                throw e
            } catch (e: Exception) {
                // Don't swallow cancellation of our own scope.
                currentCoroutineContext().ensureActive()
                // Force-stop is best-effort: log other failures and continue to clear cache.
                log(TAG, WARN) { "Force-stop automation failed: ${e.asLog()}" }
            }
        } else {
            log(TAG, WARN) { "No method available for force-stopping apps" }
        }

        // Small delay to allow apps to fully stop
        delay(500)
    }

    private suspend fun trimCachesWithAdb(targets: Collection<AppJunk>): InaccDelResult {
        log(TAG) { "Using ADB to delete inaccessible caches" }
        updateProgressPrimary(eu.darken.sdmse.appcleaner.R.string.appcleaner_progress_shizuku_deleting_caches)
        updateProgressSecondary(eu.darken.sdmse.common.R.string.general_progress_loading_app_data)

        val trimCandidates = targets.filter { !it.pkg.isSystemApp }
        updateProgressCount(Progress.Count.Counter(trimCandidates.size))

        val successTargets = mutableSetOf<InstallId>()
        val failedTargets = mutableMapOf<InstallId, Exception>()

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
                    failedTargets[junk.identifier] =
                        IllegalStateException("trimCache failed, single:${junk.identifier}")
                }
            }

            // Re-check system apps — trimCaches may have cleared their caches too.
            // StorageStatsManager doesn't update instantly after a trim: a single immediate read
            // can still report the pre-trim size, which would send an app whose cache is already
            // empty into the automation fallback, where the "clear cache" button is disabled and
            // every attempt burns the full step timeout. So wait for the numbers to settle.
            val systemTargets = targets.filter { it.pkg.isSystemApp }
            if (systemTargets.isNotEmpty()) {
                log(TAG) { "Re-checking ${systemTargets.size} system app targets after trimCaches" }
                // Rounds rather than one coroutine per target: every target is sampled in every
                // round, so none can be starved of polling by targets that never settle, and
                // withTimeoutOrNull is a true wall-clock budget for the whole re-check.
                val observed = mutableMapOf<InstallId, InaccessibleCache?>()
                withTimeoutOrNull(SYSTEM_RECHECK_TIMEOUT) {
                    val pending = systemTargets.toMutableList()
                    while (pending.isNotEmpty()) {
                        val settled = pending.filter { junk ->
                            val beforeInfo = junk.inaccessibleCache!!
                            val newInfo = inaccessibleCacheProvider.determineCache(junk.pkg)
                            observed[junk.identifier] = newInfo
                            // A failed query won't get better by asking again in a tight loop.
                            // Zero is the end state we wait for, and a target that was already
                            // zero would never satisfy the decrease check on its own.
                            newInfo == null || newInfo.totalSize == 0L || newInfo.totalSize < beforeInfo.totalSize
                        }
                        pending.removeAll(settled)
                        if (pending.isEmpty()) break
                        log(TAG, VERBOSE) { "Waiting on ${pending.size} system app cache sizes to settle" }
                        delay(500)
                    }
                } ?: log(TAG, WARN) { "System app re-check timed out after $SYSTEM_RECHECK_TIMEOUT" }

                systemTargets.forEach { junk ->
                    val beforeInfo = junk.inaccessibleCache!!
                    val identifier = junk.identifier
                    if (!observed.containsKey(identifier)) {
                        log(TAG, WARN) { "System app cache never sampled (re-check timed out): $identifier" }
                        return@forEach
                    }
                    when (val newInfo = observed[identifier]) {
                        null -> log(TAG, WARN) { "System app cache unknown (query failed): $identifier" }

                        // An app whose cache is already empty has nothing left for automation to
                        // clear, so accept it regardless of whether we observed a decrease.
                        else -> when {
                            newInfo.totalSize == 0L -> {
                                log(TAG) { "System app cache is empty: $identifier" }
                                successTargets.add(identifier)
                            }

                            newInfo.totalSize < beforeInfo.totalSize -> {
                                log(TAG) { "System app cache decreased after trimCaches: $identifier" }
                                successTargets.add(identifier)
                            }

                            else -> log(TAG, VERBOSE) { "System app cache unchanged: $identifier" }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Don't swallow cancellation of our own scope (e.g. user cancelled the task).
            // This block suspends for seconds, so a cancel landing inside it is not unlikely.
            currentCoroutineContext().ensureActive()
            log(TAG, ERROR) { "Trimming caches failed: ${e.asLog()}" }
            trimCandidates.forEach {
                failedTargets[it.identifier] = IllegalStateException("trimCache failed, multi:${it.identifier}")
            }
        }

        return InaccDelResult(
            succesful = successTargets,
            failed = failedTargets
        )
    }

    data class InaccDelResult(
        val succesful: Set<InstallId> = emptySet(),
        val failed: Map<InstallId, Exception> = emptyMap(),
    )

    companion object {
        private val TAG = logTag("AppCleaner", "Deleter", "Inaccessible")

        /** Wall-clock budget for waiting on StorageStatsManager to catch up after a trim. */
        private val SYSTEM_RECHECK_TIMEOUT = 10.seconds
    }
}
