package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.R
import eu.darken.sdmse.appcleaner.core.automation.ClearCacheTask
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCacheProvider
import eu.darken.sdmse.automation.core.AutomationSubmitter
import eu.darken.sdmse.automation.core.ForceStopAutomationTask
import eu.darken.sdmse.automation.core.errors.AutomationNoConsentException
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
import eu.darken.sdmse.main.core.GeneralSettings
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
    private val generalSettings: GeneralSettings,
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
        /**
         * Invoked with whatever was already cleared when this call is about to fail. Caches cleared
         * before a terminal automation error are genuinely gone, so the caller still has to fold
         * them into its state instead of discarding them along with the exception.
         */
        onPartialResult: (InaccDelResult) -> Unit = {},
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
            onPartialResult = onPartialResult,
        )
    }

    private suspend fun deleteInaccessible(
        rawTargets: Collection<AppJunk>,
        isAllApps: Boolean,
        useAutomation: Boolean,
        isBackground: Boolean,
        onPartialResult: (InaccDelResult) -> Unit,
    ): InaccDelResult {
        // Only a whole-tool clean issues the device-global trim, and only then are exclusion-limited
        // junks part of the scan results at all. Without the trim there is no reason to touch them.
        val willTrim = adbManager.canUseAdbNow() && isAllApps
        val targets = when {
            !isAllApps -> rawTargets
            willTrim -> rawTargets
            // Whole-tool clean without the trim: nothing will reach these anyway.
            else -> rawTargets.filter { !it.isExclusionLimited }
        }

        log(TAG) { "${targets.size} inaccessible caches to delete." }
        if (targets.isEmpty()) return InaccDelResult()

        val successTargets = mutableListOf<InstallId>()
        val failedTargets = mutableMapOf<InstallId, Exception>()
        // What each target held before we touched it. The scan-time size is the right baseline for
        // the ADB backend, which runs first. Targets that survive to the ACS stage are re-based on
        // the pre-ACS re-query below, so ACS is not credited with what ADB already freed.
        val baselines = targets.associate { it.identifier to it.inaccessibleCache!!.totalSize }.toMutableMap()
        val freedBytes = mutableMapOf<InstallId, Long>()
        // Already empty before any backend ran: a success with nothing to measure. Excluded from
        // the observation so it keeps contributing its scan-time size, exactly as before.
        val zeroCacheSkips = mutableSetOf<InstallId>()

        // The trim can credit a limited target with a success, but a per-app clear was never
        // attempted for it, so it must not be able to report a failure either.
        val limitedIds = targets.filter { it.isExclusionLimited }.map { it.identifier }.toSet()

        if (willTrim) {
            val adbResult = trimCachesWithAdb(targets)
            successTargets.addAll(adbResult.succesful)
            failedTargets.putAll(adbResult.failed.filterKeys { it !in limitedIds })
        }

        val remainingTargets = targets
            .filter { !successTargets.contains(it.identifier) }
            // The trim covered these and may have cleared them, but driving their settings page or
            // force-stopping them are actions the exclusion ruled out. A limited target the trim
            // did not confirm is left alone rather than reported as a failed attempt. A targeted
            // clean of such an entry is an explicit user action and keeps the full treatment.
            .filter { !willTrim || !it.isExclusionLimited }
            .filter { junk ->
                val currentCache = inaccessibleCacheProvider.determineCache(junk.pkg)
                if (currentCache != null && currentCache.totalSize == 0L) {
                    log(TAG) { "Cache now zero, skipping automation: ${junk.identifier}" }
                    successTargets.add(junk.identifier)
                    zeroCacheSkips.add(junk.identifier)
                    false
                } else {
                    // Free re-baseline: this query is made anyway, we just stopped throwing the
                    // value away. A failed query leaves the scan-time baseline in place.
                    if (currentCache != null) baselines[junk.identifier] = currentCache.totalSize
                    true
                }
            }

        // `null` = never asked, `false` = declined. Neither can be turned into a prompt from here:
        // the submitter throws instead of asking, so the stage can only fail. Skip it and let the
        // caller decide how loud that has to be.
        val hasAcsConsent = generalSettings.hasAcsConsent.value() == true
        var noConsentSkip = false

        if (useAutomation && remainingTargets.isNotEmpty() && hasAcsConsent) {
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
                        return buildResult(successTargets, failedTargets)
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
                } catch (e: AutomationNoConsentException) {
                    // Consent can be withdrawn between the check above and this submission.
                    log(TAG, WARN) { "No ACS consent, skipping the accessibility stage: $successLive" }
                    noConsentSkip = true
                    ClearCacheTask.Result(
                        successful = successLive,
                        failed = failedLive,
                    )
                } catch (e: AutomationUnavailableException) {
                    // Nothing ran, but fold anyway so the partial-result contract holds uniformly.
                    successTargets.addAll(successLive)
                    failedTargets.putAll(failedLive)
                    onPartialResult(buildResult(successTargets, failedTargets))
                    throw InaccessibleDeletionException(e)
                } catch (e: UserCancelledAutomationException) {
                    log(TAG, WARN) { "User has cancelled ($e), forwarding live progress: $successLive" }
                    ClearCacheTask.Result(
                        successful = successLive,
                        failed = failedLive,
                    )
                } catch (e: Exception) {
                    // Any other terminal automation failure: a compatibility give-up, a locked
                    // screen, an overlay error, or the caller being cancelled. Caches cleared
                    // before that point are really cleared, so hand them back before the exception
                    // travels on, otherwise the next scan still lists them as freeable.
                    log(TAG, WARN) { "ACS clearing failed, forwarding live progress: $successLive" }
                    successTargets.addAll(successLive)
                    failedTargets.putAll(failedLive)
                    onPartialResult(buildResult(successTargets, failedTargets))
                    throw e
                }

                successTargets.addAll(result.successful)
                failedTargets.putAll(result.failed)
            }
        } else if (useAutomation && remainingTargets.isNotEmpty()) {
            log(TAG, WARN) { "No ACS consent, skipping the accessibility stage for ${remainingTargets.size} targets" }
            noConsentSkip = true
        } else if (!useAutomation) {
            log(TAG, INFO) { "useAutomation=false" }
        }

        val skippedNoConsent = when {
            noConsentSkip -> remainingTargets
                .map { it.identifier }
                .filter { !successTargets.contains(it) && !failedTargets.containsKey(it) }
                .toSet()

            else -> emptySet()
        }

        val observationTargets = targets.filter {
            successTargets.contains(it.identifier) && !zeroCacheSkips.contains(it.identifier)
        }
        if (observationTargets.isNotEmpty()) {
            try {
                freedBytes.putAll(observeFreedBytes(observationTargets, baselines))
            } catch (e: Exception) {
                // This suspends for up to ACS_SETTLE_TIMEOUT, so a cancel landing inside it is not
                // unlikely. Without this the caller would receive no result at all and would keep
                // advertising caches that are genuinely gone.
                log(TAG, WARN) { "Freed-byte observation failed, forwarding what was cleared: ${e.asLog()}" }
                onPartialResult(buildResult(successTargets, failedTargets, freedBytes, skippedNoConsent))
                throw e
            }
        }

        return buildResult(successTargets, failedTargets, freedBytes, skippedNoConsent)
    }

    /**
     * Measures how many bytes each cleared target actually gave up. "Cache cleared" is what a
     * backend reports, not what StorageStatsManager confirms: an app can report a successful clear
     * and still hold every byte, and a real clear needs a moment for the numbers to catch up. The
     * outcome is a number only, no target is reclassified based on what is observed here.
     */
    private suspend fun observeFreedBytes(
        targets: Collection<AppJunk>,
        baselines: Map<InstallId, Long>,
    ): Map<InstallId, Long> {
        log(TAG) { "Observing freed bytes for ${targets.size} targets" }
        // The automation stage is over by now and SD Maid is back in the foreground, so leaving the
        // "preparing automation" label up would describe a step that already finished.
        updateProgressPrimary(eu.darken.sdmse.appcleaner.R.string.appcleaner_progress_checking_freed_space)
        updateProgressSecondary(CaString.EMPTY)
        updateProgressCount(Progress.Count.Percent(targets.size))

        val minObserved = mutableMapOf<InstallId, Long>()
        val lastSample = mutableMapOf<InstallId, Long>()
        // A read that failed after an earlier one succeeded must not let that earlier value pass as
        // the final measurement, or a 100MB cache read as 90MB and then unreadable reports 10MB.
        val failedReads = mutableSetOf<InstallId>()
        // Reads that all came back at exactly the pre-clear size, per target. -1 once a target has
        // read anything else: a number that has moved is on its way somewhere and gets the full
        // budget, however many times it reads its old size afterwards.
        val pinnedReads = mutableMapOf<InstallId, Int>()

        // Track the MINIMUM, not the first decrease. A 100MB cache sampled as 90MB and then 0MB
        // freed 100MB; stopping at the first decrease would report 10MB, which is exactly the kind
        // of wrong number this observation exists to eliminate.
        suspend fun sample(junk: AppJunk): Boolean {
            val id = junk.identifier
            val current = inaccessibleCacheProvider.determineCache(junk.pkg)
            if (current == null) {
                // A failed query won't get better by asking again in a tight loop.
                log(TAG, WARN) { "Freed-byte sample failed for $id" }
                failedReads.add(id)
                return true
            }
            val size = current.totalSize
            minObserved[id] = minOf(minObserved[id] ?: Long.MAX_VALUE, size)
            val previous = lastSample.put(id, size)
            val baseline = baselines[id] ?: junk.inaccessibleCache!!.totalSize
            // Zero is the end state. Otherwise the number has to move off the pre-clear size AND
            // stop moving before we trust it: StorageStatsManager lags behind a clear, so two reads
            // 500ms apart can both still report the stale pre-clear size.
            if (size == 0L || (previous == size && size < baseline)) return true

            // A number that has not once moved off the pre-clear size can never satisfy the rule
            // above, so it would sit in the poll to the end of the budget and be reported as nothing
            // freed anyway, making every round in between cost one more query for the targets that
            // are still moving. Giving up on it early buys that back. The price is a cache whose
            // size only starts falling after [PINNED_READ_LIMIT] reads, which the budget itself
            // would still have caught.
            val pinned = pinnedReads[id] ?: 0
            if (pinned < 0 || size != baseline) {
                pinnedReads[id] = -1
                return false
            }
            val reads = pinned + 1
            pinnedReads[id] = reads
            if (reads >= PINNED_READ_LIMIT) {
                log(TAG) { "Still at pre-clear size $baseline after $reads reads, giving up on $id" }
                return true
            }
            return false
        }

        // First pass outside the budget: on a large batch a global timeout can expire mid-round and
        // leave late targets unsampled, silently back on the optimistic pre-clear figure. Every
        // target is sampled at least once, always.
        val pending = mutableListOf<AppJunk>()
        targets.forEach { if (sample(it)) increaseProgress() else pending.add(it) }

        if (pending.isNotEmpty()) {
            // Rounds rather than one coroutine per target, like the system app re-check above: no
            // target can be starved of polling by targets that never settle.
            withTimeoutOrNull(ACS_SETTLE_TIMEOUT) {
                while (pending.isNotEmpty()) {
                    log(TAG, VERBOSE) { "Waiting on ${pending.size} cache sizes to settle" }
                    delay(500)
                    val round = pending.iterator()
                    while (round.hasNext()) {
                        // sample() suspends, so the budget can expire inside it. Removing and
                        // counting per target keeps what this round already established, instead of
                        // discarding it and naming settled targets in the timeout warning below.
                        if (sample(round.next())) {
                            round.remove()
                            increaseProgress()
                        }
                    }
                }
            } ?: log(TAG, WARN) {
                "Freed-byte observation timed out after $ACS_SETTLE_TIMEOUT for: ${pending.map { it.identifier }}"
            }
        }
        // Hand the next phase a spinner rather than our finished ring.
        updateProgressCount(Progress.Count.Indeterminate())

        return targets.associate { junk ->
            val id = junk.identifier
            val baseline = baselines[id] ?: junk.inaccessibleCache!!.totalSize
            val min = minObserved[id]
            when {
                // Never got a single reading, or a reading failed part-way through. Reporting 0
                // here would tell a user who really did get their space back that nothing happened,
                // and crediting a half-finished walk-down would under-report just as badly, so fall
                // back to the pre-clear size and make the failed measurement visible instead of
                // letting it pass as a real one.
                id in failedReads || min == null -> {
                    log(TAG, WARN) { "Freed-byte read failed, falling back to pre-clear size for $id" }
                    id to baseline
                }

                else -> {
                    val freed = (baseline - min).coerceAtLeast(0L)
                    log(TAG) { "Freed $freed of $baseline bytes (smallest observed: $min) for $id" }
                    id to freed
                }
            }
        }
    }

    /**
     * Snapshots the accumulated per-app outcomes. Also cleans up contradictory bookkeeping: an app
     * that failed earlier but succeeded later must not stay in [InaccDelResult.failed], or it would
     * be recorded as a permanent ACS failure and marked unclearable.
     */
    private fun buildResult(
        successTargets: Collection<InstallId>,
        failedTargets: Map<InstallId, Exception>,
        freedBytes: Map<InstallId, Long> = emptyMap(),
        skippedNoConsent: Set<InstallId> = emptySet(),
    ): InaccDelResult {
        val successful = successTargets.toSet()
        return InaccDelResult(
            succesful = successful,
            failed = failedTargets.filterKeys { !successful.contains(it) },
            freedBytes = freedBytes.filterKeys { successful.contains(it) },
            skippedNoConsent = skippedNoConsent,
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
        /**
         * Bytes that were observed to actually disappear, per app. A missing entry means the
         * clear was not measured; the caller then falls back to the pre-clear size, which is
         * what it reported unconditionally before this existed.
         */
        val freedBytes: Map<InstallId, Long> = emptyMap(),
        /**
         * Targets the accessibility stage never touched because SD Maid has no consent to use the
         * service. Disjoint from [succesful] and [failed]: an app the ADB stage already failed on
         * has a real per-app error and must keep it.
         */
        val skippedNoConsent: Set<InstallId> = emptySet(),
    )

    companion object {
        private val TAG = logTag("AppCleaner", "Deleter", "Inaccessible")

        /** Wall-clock budget for waiting on StorageStatsManager to catch up after a trim. */
        private val SYSTEM_RECHECK_TIMEOUT = 10.seconds

        /** Wall-clock budget for the whole post-clear settle poll that measures freed bytes. */
        private val ACS_SETTLE_TIMEOUT = 4.seconds

        /**
         * Reads, all at exactly the pre-clear size, after which a target is dropped from the settle
         * poll. The first pass is read 1 and rounds are 500ms apart, so this is 2.5s of a number
         * that has never moved, against the 4s of [ACS_SETTLE_TIMEOUT].
         */
        private const val PINNED_READ_LIMIT = 6
    }
}
