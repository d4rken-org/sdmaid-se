package eu.darken.sdmse.automation.core.common

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.Reusable
import eu.darken.sdmse.automation.core.ScreenState
import eu.darken.sdmse.automation.core.errors.AutomationException
import eu.darken.sdmse.automation.core.errors.PlanAbortException
import eu.darken.sdmse.automation.core.errors.ScreenUnavailableException
import eu.darken.sdmse.automation.core.errors.StepAbortException
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.waitForWindowRoot
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.CaString
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

    suspend fun process(context: AutomationExplorer.Context, step: Step): Unit = withTimeout(step.timeout) {
        val tag = step.source + ":Stepper"
        log(tag) { "crawl(): $step" }
        updateProgressPrimary(step.label)
        var attempts = 0

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
                    try {
                        val stepContext = Context(
                            hostContext = context,
                            tag = step.source,
                            attempt = attempts++,
                        )
                        withTimeout(5 * 1000) {
                            val stepTime = measureTimeMillis {
                                doCrawl(stepContext, step)
                            }
                            log(tag) { "Step took ${stepTime}ms to execute" }
                        }
                        // Step was successful :))
                        break
                    } catch (e: PlanAbortException) {
                        log(tag, WARN) { "ABORT Plan due to ${e.asLog()}" }
                        throw e
                    } catch (e: StepAbortException) {
                        log(tag, WARN) { "ABORT Step due to ${e.asLog()}" }
                        break
                    } catch (e: Exception) {
                        log(tag, WARN) { "crawl(): Attempt $attempts failed on $step:\n${e.asLog()}" }
                        delay(300)
                    }
                }
            } finally {
                screenGuardJob.cancel()
            }
        }
    }

    private suspend fun doCrawl(context: Context, step: Step) {
        val tag = context.tag + ":Stepper"
        log(tag, VERBOSE) { "doCrawl(): context=$context for $step" }

        when {
            context.attempt > 1 -> when {
                hasApiLevel(31) -> {
                    log(tag) { "To dismiss any notification shade" }
                    @Suppress("NewApi")
                    context.host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                }

                !hasApiLevel(31) -> {
                    log(tag) { "Clearing system dialogs (retryCount=${context.attempt})." }
                    @Suppress("DEPRECATION")
                    val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                    try {
                        context.host.service.sendBroadcast(closeIntent)
                    } catch (e: Exception) {
                        log(tag, WARN) { "Sending ACTION_CLOSE_SYSTEM_DIALOGS failed: ${e.asLog()}" }
                    }
                }
            }
        }


        if (step.windowLaunch != null) {
            log(tag, INFO) { "Executing windowLaunch: ${step.windowLaunch}" }
            step.windowLaunch.invoke(context)
            log(tag) { "Window launched!" }
        }

        // avg delay between activity launch and acs event
        delay(50)

        val targetWindowRoot: AccessibilityNodeInfo = withTimeout(4000) {
            if (step.windowCheck != null) {
                log(tag) { "Executing windowCheck and determining root window..." }
                step.windowCheck.invoke(context)
            } else {
                log(tag) { "No window check set, waiting for any root window..." }
                context.host.waitForWindowRoot()
            }
        }
        log(tag, DEBUG) { "Current window root node is ${targetWindowRoot.toStringShort()}" }

        val targetNode: AccessibilityNodeInfo = when {
            step.nodeTest != null -> {
                var target: AccessibilityNodeInfo? = null
                var currentRootNode = targetWindowRoot

                while (currentCoroutineContext().isActive) {
                    if (Bugs.isDebug) {
                        log(tag, VERBOSE) { "Checking current nodes:" }
                        currentRootNode.crawl().forEach { log(tag, VERBOSE) { it.infoShort } }
                    }

                    target = currentRootNode.crawl().map { it.node }.find { step.nodeTest.invoke(it) }

                    if (target != null) {
                        log(tag, INFO) { "Target node found: ${target.toStringShort()}" }
                        break
                    } else {
                        log(tag, WARN) { "Target node not found" }
                    }

                    if (step.nodeRecovery != null) {
                        log(tag, VERBOSE) { "Trying node recovery!" }
                        // Should we care about whether the recovery thinks it was successful?
                        step.nodeRecovery.invoke(currentRootNode)
                        delay(200)
                    } else {
                        // Timeout will hit here and cancel if necessary
                        delay(100)
                    }
                    // Let's try a new one
                    currentRootNode = context.host.waitForWindowRoot()
                }
                target!!
            }

            else -> context.host.waitForWindowRoot()
        }

        // e.g. find a clickable parent based on the target node
        val mappedNode = step.nodeMapping
            ?.also { log(tag, DEBUG) { "Trying to map ${targetNode.toStringShort()} using $it" } }
            ?.invoke(targetNode)
            ?.also { log(tag, INFO) { "Mapped node is ${it.toStringShort()}" } }
            ?: targetNode.also { log(tag, VERBOSE) { "No mapping to be done." } }

        // Perform action, e.g. clicking a button
        log(tag, INFO) { "Performing action on $mappedNode" }
        val success = step.action?.invoke(mappedNode, context.attempt) != false

        if (success) {
            log(tag, INFO) { "Crawl was successful :)" }
        } else {
            throw AutomationException("Action failed on $mappedNode (spec=$step)")
        }
    }

    data class Context(
        val hostContext: AutomationExplorer.Context,
        val tag: String,
        val attempt: Int,
    ) : AutomationExplorer.Context by hostContext

    data class Step(
        val source: String,
        val descriptionInternal: String,
        val label: CaString,
        val icon: CaDrawable? = null,
        val windowLaunch: (suspend Context.() -> Unit)? = null,
        val windowCheck: (suspend Context.() -> AccessibilityNodeInfo)? = null,
        val nodeTest: (suspend (node: AccessibilityNodeInfo) -> Boolean)? = null,
        val nodeRecovery: (suspend (node: AccessibilityNodeInfo) -> Boolean)? = null,
        val nodeMapping: (suspend (node: AccessibilityNodeInfo) -> AccessibilityNodeInfo)? = null,
        val action: (suspend (node: AccessibilityNodeInfo, retryCount: Int) -> Boolean)? = null,
        val timeout: Long = 15 * 1000,
    ) {
        override fun toString(): String = "Step(source=$source, description=$descriptionInternal)"
    }

    data class Result(val success: Boolean, val exception: Exception? = null)
}