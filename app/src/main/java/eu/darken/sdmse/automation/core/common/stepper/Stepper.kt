package eu.darken.sdmse.automation.core.common.stepper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.Reusable
import eu.darken.sdmse.automation.core.ScreenState
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.toStringShort
import eu.darken.sdmse.automation.core.errors.AutomationException
import eu.darken.sdmse.automation.core.errors.PlanAbortException
import eu.darken.sdmse.automation.core.errors.ScreenUnavailableException
import eu.darken.sdmse.automation.core.errors.StepAbortException
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.waitForWindowRoot
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressPrimary
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import kotlin.system.measureTimeMillis

@Reusable
class Stepper @Inject constructor(
    private val screenState: ScreenState,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.Data(primary = R.string.general_progress_preparing.toCaString())
    )

    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(50)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun process(context: AutomationExplorer.Context, step: AutomationStep): Unit = withTimeout(step.timeout) {
        val tag = step.source + ":Stepper"
        log(tag, INFO) { "process(): $step" }
        updateProgressPrimary(step.label)
        var stepAttempts = 0

        // If the lock screen becomes active, immediately cancel any ACS operations
        // The user probably needs the phone. Normally, the screen does not turn off while our automation is active.
        val screenGuard = screenState.state
            .filter { !it.isScreenAvailable }
            .take(1)
            .onEach { throw ScreenUnavailableException("Screen is unavailable!") }

        coroutineScope {
            val screenGuardJob = screenGuard.launchIn(this)

            try {
                while (currentCoroutineContext().isActive) {
                    val stepContext = StepContext(
                        hostContext = context,
                        tag = step.source,
                        stepAttempts = stepAttempts++,
                    )
                    try {
                        withTimeout(5 * 1000) {
                            val stepTime = measureTimeMillis {
                                doProcess(stepContext, step)
                            }
                            log(tag) { "Step took ${stepTime}ms to execute" }
                        }
                        // Step was successful :))
                        break
                    } catch (e: PlanAbortException) {
                        log(tag, WARN) { "ABORT Plan due to ${e.asLog()}" }
                        logCurrentNodes(tag, context)
                        throw e
                    } catch (e: StepAbortException) {
                        log(tag, WARN) { "ABORT Step due to ${e.asLog()}" }
                        logCurrentNodes(tag, context)
                        break
                    } catch (e: Exception) {
                        log(tag, WARN) { "crawl(): Attempt $stepAttempts failed on $step:\n${e.asLog()}" }
                        logCurrentNodes(tag, context)
                        delay(300)
                    }
                }
            } finally {
                screenGuardJob.cancel()
            }
        }
    }

    private suspend fun doProcess(stepContext: StepContext, step: AutomationStep) {
        val tag = stepContext.tag + ":Stepper"
        log(tag, VERBOSE) { "doProcess(): context=$stepContext for $step" }

        when {
            stepContext.stepAttempts > 1 -> when {
                hasApiLevel(31) -> {
                    log(tag) { "Trying to dismiss any notification shade" }
                    @Suppress("NewApi")
                    stepContext.host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                }

                !hasApiLevel(31) -> {
                    log(tag) { "Clearing system dialogs (retryCount=${stepContext.stepAttempts})." }
                    @Suppress("DEPRECATION")
                    val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                    try {
                        stepContext.host.service.sendBroadcast(closeIntent)
                    } catch (e: Exception) {
                        log(tag, WARN) { "Sending ACTION_CLOSE_SYSTEM_DIALOGS failed: ${e.asLog()}" }
                    }
                }
            }
        }

        if (step.windowLaunch != null) {
            log(tag, INFO) { "Executing windowLaunch: ${step.windowLaunch}" }
            step.windowLaunch.invoke(stepContext)
            log(tag) { "Window launched!" }
        }

        // avg delay between activity launch and acs event
        delay(50)

        val targetWindowRoot: AccessibilityNodeInfo = withTimeout(4000) {
            if (step.windowCheck != null) {
                log(tag) { "Executing windowCheck and determining root window..." }
                step.windowCheck.invoke(stepContext)
            } else {
                log(tag) { "No window check set, waiting for any root window..." }
                stepContext.host.waitForWindowRoot()
            }
        }
        log(tag, DEBUG) { "Current window root node is ${targetWindowRoot.toStringShort()}" }

        if (step.nodeAction != null) {
            // Perform action, e.g. clicking a button
            log(tag, INFO) { "Performing action... ${step.nodeAction}" }
            var success = false
            while (currentCoroutineContext().isActive) {
                success = step.nodeAction.invoke(stepContext)
                if (success) {
                    break
                } else if (step.nodeRecovery != null) {
                    log(tag, VERBOSE) { "Trying node recovery!" }
                    // TODO Should we care about whether the recovery thinks it was successful?
                    step.nodeRecovery.invoke(stepContext, stepContext.host.waitForWindowRoot())
                    delay(200)
                } else {
                    // Timeout will hit here and cancel if necessary
                    delay(100)
                }
            }

            if (success) {
                log(tag, INFO) { "nodeAction was successful :)" }
            } else {
                throw AutomationException("nodeAction failed (spec=$step, context=$stepContext)")
            }
        }

        log(tag, INFO) { "Step ended without error" }
    }

    private suspend fun logCurrentNodes(tag: String, context: AutomationExplorer.Context) {
        if (Bugs.isDebug) {
            log(tag, WARN) { "Current nodes:" }
            context.host.windowRoot()?.crawl()?.forEach { log(tag, WARN) { it.infoShort } }
        }
    }

    data class Result(val success: Boolean, val exception: Exception? = null)
}