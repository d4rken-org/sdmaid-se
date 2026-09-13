package eu.darken.sdmse.appcleaner.core.automation

import eu.darken.sdmse.automation.core.errors.AutomationOverlayException
import eu.darken.sdmse.automation.core.errors.AutomationTimeoutException
import eu.darken.sdmse.automation.core.errors.InvalidSystemStateException
import eu.darken.sdmse.automation.core.errors.PlanAbortException
import eu.darken.sdmse.automation.core.errors.StepAbortException
import eu.darken.sdmse.automation.core.errors.UserCancelledAutomationException
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import kotlinx.coroutines.CancellationException

/**
 * Clears the cache of each `task.targets` entry via [clearOne] and records what happened to it.
 *
 * Both ways a target can come back cleared - [clearOne] returning, and a [PlanAbortException] the
 * spec flagged as `treatAsSuccess` - report through `task.onSuccess`. That callback is the only
 * channel that survives an interrupted run: the return value here never reaches AppCleaner if the
 * accessibility service dies mid-run.
 *
 * [resolveOne] runs per target, immediately before that target is cleared, so an early exit skips
 * the lookups for everything after it.
 *
 * [onTick] carries the caller's per-target bookkeeping (ops counter).
 */
internal suspend fun Progress.Client.clearCachesFor(
    task: ClearCacheTask,
    resolveOne: suspend (InstallId) -> Installed?,
    onTick: (Pkg.Id) -> Unit,
    clearOne: suspend (Installed) -> Unit,
): ClearCacheModule.ProcessedTask {
    val successful = mutableSetOf<InstallId>()
    val failed = mutableMapOf<InstallId, Exception>()
    var cancelledByUser = false

    for (target in task.targets) {
        val installed = resolveOne(target)

        if (installed == null) {
            log(TAG, WARN) { "$target is not in package repo" }
            failed[target] = IllegalStateException("$target is not in package repo")
            continue
        }

        log(TAG) { "Clearing cache for $installed" }
        updateProgressPrimary(installed.label ?: target.pkgId.name.toCaString())

        try {
            clearOne(installed)
            log(TAG, INFO) { "Successfully cleared cache for for $target" }
            task.onSuccess(target)
            successful.add(target)
        } catch (e: Exception) {
            when {
                e is InvalidSystemStateException -> {
                    log(TAG, WARN) { "Invalid system state for ACS based cache deletion: ${e.asLog()}" }
                    throw e
                }

                e.isAutomationUnusable() -> {
                    log(TAG, WARN) { "Automation unusable while processing $installed: ${e.asLog()}" }
                    task.onError(target, e)
                    failed[target] = e
                    val unusable = failed.count { it.value.isAutomationUnusable() }
                    if (successful.isEmpty() && unusable >= FAILURE_LIMIT) break
                }

                e is AutomationOverlayException -> {
                    log(TAG, ERROR) { "Automation overlay error: ${e.asLog()}" }
                    throw e
                }

                e is PlanAbortException && e.treatAsSuccess -> {
                    log(TAG, INFO) { "Treating aborted plan as success for $target:\n${e.asLog()}" }
                    task.onSuccess(target)
                    successful.add(target)
                }

                e is CancellationException -> {
                    log(TAG, WARN) { "We were cancelled: ${e.asLog()}" }
                    updateProgressPrimary(eu.darken.sdmse.common.R.string.general_cancel_action)
                    updateProgressSecondary(CaString.EMPTY)
                    updateProgressCount(Progress.Count.Indeterminate())
                    if (e is UserCancelledAutomationException) {
                        log(TAG, INFO) { "User has cancelled automation process, aborting..." }
                        cancelledByUser = true
                        break
                    } else {
                        throw e
                    }
                }

                else -> {
                    log(TAG, WARN) { "Failure for $target:\n${e.asLog()}" }
                    task.onError(target, e)
                    failed[target] = e
                }
            }
        } finally {
            increaseProgress()
            onTick(target.pkgId)
        }
    }

    return ClearCacheModule.ProcessedTask(
        successful = successful,
        failed = failed,
        cancelledByUser = cancelledByUser,
    )
}

/** How many targets may fail with an unusable automation path before we stop trying. */
internal const val FAILURE_LIMIT = 8

/**
 * Failures that mean we could not drive the Settings UI at all, as opposed to one app
 * being uncooperative. A timeout is the slow form, an unretryable step abort the fast one
 * (e.g. the DPAD fallback finding the clear-cache button unreachable). Both indicate the
 * automation path itself is broken, so both feed the give-up heuristic.
 */
internal fun Throwable.isAutomationUnusable(): Boolean = when (this) {
    is AutomationTimeoutException -> true
    is StepAbortException -> !treatAsSuccess
    else -> false
}

private val TAG: String = ClearCacheModule.TAG
