package eu.darken.sdmse.appcontrol.core.automation

import eu.darken.sdmse.automation.core.errors.AutomationOverlayException
import eu.darken.sdmse.automation.core.errors.AutomationTimeoutException
import eu.darken.sdmse.automation.core.errors.InvalidSystemStateException
import eu.darken.sdmse.automation.core.errors.UnusableFailureBudget
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
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.user.UserHandle2
import kotlinx.coroutines.CancellationException

internal data class AutomationLoopResult(
    val successful: Set<InstallId>,
    val failed: Map<InstallId, Exception>,
    val cancelledByUser: Boolean,
    val gaveUp: Boolean,
)

/**
 * Drives [processOne] over each of [targets] and records what happened to it.
 *
 * Targets that never reached the automation path - an [offLimitPkgs] match, or a target [resolveOne]
 * can't map to an installed package - are failed without spending failure budget: nothing was
 * attempted, so they say nothing about whether automation works on this device.
 *
 * Failures that mean the automation path itself is unusable are counted, and once enough of them
 * pile up the loop stops with [AutomationLoopResult.gaveUp] set. Successes do not pay that budget
 * back, a device where most targets time out is broken even if a few happen to work.
 *
 * [resolveOne] runs per target, immediately before that target is processed, so an early exit skips
 * the lookups for everything after it.
 */
internal suspend fun Progress.Client.automateEachTarget(
    targets: List<InstallId>,
    currentUserHandle: UserHandle2,
    actionLabel: String,
    unsupportedForOtherUsers: String,
    offLimitPkgs: Set<Pkg.Id> = emptySet(),
    resolveOne: suspend (InstallId) -> Installed?,
    processOne: suspend (Installed) -> Unit,
): AutomationLoopResult {
    val successful = mutableSetOf<InstallId>()
    val failed = mutableMapOf<InstallId, Exception>()
    var cancelledByUser = false
    var gaveUp = false
    val budget = UnusableFailureBudget()

    for ((index, target) in targets.withIndex()) {
        if (target.userHandle != currentUserHandle) {
            throw UnsupportedOperationException("$unsupportedForOtherUsers ($target)")
        }

        if (offLimitPkgs.contains(target.pkgId)) {
            log(TAG, WARN) { "Skipping $target: automating it would break accessibility automation" }
            failed[target] = IllegalStateException("$target is off-limits for automation")
            continue
        }

        val installed = resolveOne(target)

        if (installed == null) {
            log(TAG, WARN) { "$target is not in package repo" }
            failed[target] = IllegalStateException("$target is not in package repo")
            continue
        }

        log(TAG) { "$actionLabel $installed" }
        updateProgressPrimary(installed.label ?: target.pkgId.name.toCaString())

        try {
            processOne(installed)
            log(TAG, INFO) { "$actionLabel succeeded for $target" }
            successful.add(target)
        } catch (e: Exception) {
            when {
                e is InvalidSystemStateException -> {
                    log(TAG, WARN) { "Invalid system state: ${e.asLog()}" }
                    throw e
                }

                e is AutomationOverlayException -> {
                    log(TAG, ERROR) { "Automation overlay error: ${e.asLog()}" }
                    throw e
                }

                e is UnsupportedOperationException -> {
                    log(TAG, ERROR) { "Unsupported operation error: ${e.asLog()}" }
                    throw e
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
                    if (e is AutomationTimeoutException) {
                        log(TAG, WARN) { "Timeout while processing $installed: $e" }
                    } else {
                        log(TAG, WARN) { "Failure for $target: ${e.asLog()}" }
                    }
                    failed[target] = e
                    budget.onFailure(e)
                    if (budget.isExhausted) {
                        log(TAG, WARN) { "Failure budget exhausted after $target, giving up" }
                        gaveUp = true
                        break
                    }
                }
            }
        } finally {
            updateProgressCount(Progress.Count.Percent(index, targets.size))
        }
    }

    return AutomationLoopResult(
        successful = successful,
        failed = failed,
        cancelledByUser = cancelledByUser,
        gaveUp = gaveUp,
    )
}

private val TAG: String = AppControlAutomation.TAG
