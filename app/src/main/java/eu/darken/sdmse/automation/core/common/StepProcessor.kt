package eu.darken.sdmse.automation.core.common

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.automation.core.AutomationHost
import eu.darken.sdmse.automation.core.ScreenState
import eu.darken.sdmse.automation.core.errors.AutomationException
import eu.darken.sdmse.automation.core.errors.PlanAbortException
import eu.darken.sdmse.automation.core.errors.ScreenUnavailableException
import eu.darken.sdmse.automation.core.errors.StepAbortException
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
import eu.darken.sdmse.common.debug.logging.logTag
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlin.system.measureTimeMillis


class StepProcessor @AssistedInject constructor(
    @Assisted private val host: AutomationHost,
    private val screenState: ScreenState,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.Data(primary = R.string.general_progress_preparing.toCaString())
    )

    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(50)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun process(step: Step): Unit = withTimeout(step.timeout) {
        log(TAG) { "crawl(): $step" }
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
                        withTimeout(5 * 1000) {
                            val stepTime = measureTimeMillis {
                                doCrawl(step, attempts++)
                            }
                            log(TAG) { "Step took ${stepTime}ms to execute" }
                        }
                        // Step was successful :))
                        break
                    } catch (e: PlanAbortException) {
                        log(TAG, WARN) { "ABORT Plan due to ${e.asLog()}" }
                        throw e
                    } catch (e: StepAbortException) {
                        log(TAG, WARN) { "ABORT Step due to ${e.asLog()}" }
                        break
                    } catch (e: Exception) {
                        log(TAG, WARN) { "crawl(): Attempt $attempts failed on $step:\n${e.asLog()}" }
                        delay(300)
                    }
                }
            } finally {
                screenGuardJob.cancel()
            }
        }
    }

    private suspend fun doCrawl(step: Step, attempt: Int = 0) {
        log(TAG, VERBOSE) { "doCrawl(): Attempt $attempt for $step" }

        when {
            attempt > 1 -> when {
                hasApiLevel(31) -> {
                    log(TAG) { "To dismiss any notification shade" }
                    @Suppress("NewApi")
                    host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                }

                !hasApiLevel(31) -> {
                    log(TAG) { "Clearing system dialogs (retryCount=$attempt)." }
                    val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                    try {
                        host.service.sendBroadcast(closeIntent)
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Sending ACTION_CLOSE_SYSTEM_DIALOGS failed: ${e.asLog()}" }
                    }
                }
            }
        }

        if (step.windowIntent != null) {
            log(TAG) { "Launching window intent: ${step.windowIntent}" }
            host.service.startActivity(step.windowIntent)
        }

        // avg delay between activity launch and acs event
        delay(50)

        val targetWindowRoot: AccessibilityNodeInfo = withTimeout(4000) {
            // Wait for correct window
            if (step.windowIntent != null && step.windowEventFilter != null) {
                log(TAG) { "Waiting for window event filter to pass..." }
                host.events.filter {
                    log(TAG, VERBOSE) { "Testing window event $it" }
                    step.windowEventFilter.invoke(it)
                }.first()
                log(TAG) { "Window event filter passed!" }
            }

            // Condition for the right window, e.g. check title
            var currentRoot: AccessibilityNodeInfo? = null
            while (step.windowNodeTest != null && currentCoroutineContext().isActive) {
                currentRoot = host.waitForWindowRoot().apply {
                    if (Bugs.isDebug) {
                        log(TAG, VERBOSE) { "Looking for viable window root, current nodes:" }
                        crawl().forEach { log(TAG, VERBOSE) { it.infoShort } }
                    }
                }

                if (step.windowNodeTest.invoke(currentRoot)) {
                    log(TAG, INFO) { "Window root found: $currentRoot (spec=$step)" }
                    break
                } else {
                    log(TAG) { "Not a viable root node: $currentRoot (spec=$step)" }
                    delay(500)
                }
            }

            // There was no windowNodeTest, so we continue with the first thing we got
            currentRoot ?: host.waitForWindowRoot()
        }
        log(TAG, DEBUG) { "Current window root node is ${targetWindowRoot.toStringShort()}" }

        val targetNode: AccessibilityNodeInfo = when {
            step.nodeTest != null -> {
                var target: AccessibilityNodeInfo? = null
                var currentRootNode = targetWindowRoot

                while (currentCoroutineContext().isActive) {
                    if (Bugs.isDebug) {
                        log(TAG, VERBOSE) { "Checking current nodes:" }
                        currentRootNode.crawl().forEach { log(TAG, VERBOSE) { it.infoShort } }
                    }

                    target = currentRootNode.crawl().map { it.node }.find { step.nodeTest.invoke(it) }

                    if (target != null) {
                        log(TAG, INFO) { "Target node found: ${target.toStringShort()}" }
                        break
                    } else {
                        log(TAG, WARN) { "Target node not found" }
                    }

                    if (step.nodeRecovery != null) {
                        log(TAG, VERBOSE) { "Trying node recovery!" }
                        // Should we care about whether the recovery thinks it was successful?
                        step.nodeRecovery.invoke(currentRootNode)
                        delay(200)
                    } else {
                        // Timeout will hit here and cancel if necessary
                        delay(100)
                    }
                    // Let's try a new one
                    currentRootNode = host.waitForWindowRoot()
                }
                target!!
            }

            else -> host.waitForWindowRoot()
        }

        // e.g. find a clickable parent based on the target node
        val mappedNode = step.nodeMapping
            ?.also { log(TAG, DEBUG) { "Trying to map ${targetNode.toStringShort()} using $it" } }
            ?.invoke(targetNode)
            ?.also { log(TAG, INFO) { "Mapped node is ${it.toStringShort()}" } }
            ?: targetNode.also { log(TAG, VERBOSE) { "No mapping to be done." } }

        // Perform action, e.g. clicking a button
        log(TAG, INFO) { "Performing action on $mappedNode" }
        val success = step.action?.invoke(mappedNode, attempt) != false

        if (success) {
            log(TAG, INFO) { "Crawl was successful :)" }
        } else {
            throw AutomationException("Action failed on $mappedNode (spec=$step)")
        }
    }

    data class Step(
        val source: String,
        val descriptionInternal: String,
        val label: CaString,
        val icon: CaDrawable? = null,
        val windowIntent: Intent? = null,
        val windowEventFilter: (suspend (node: AccessibilityEvent) -> Boolean)? = null,
        val windowNodeTest: (suspend (node: AccessibilityNodeInfo) -> Boolean)? = null,
        val nodeTest: (suspend (node: AccessibilityNodeInfo) -> Boolean)? = null,
        val nodeRecovery: (suspend (node: AccessibilityNodeInfo) -> Boolean)? = null,
        val nodeMapping: (suspend (node: AccessibilityNodeInfo) -> AccessibilityNodeInfo)? = null,
        val action: (suspend (node: AccessibilityNodeInfo, retryCount: Int) -> Boolean)? = null,
        val timeout: Long = 15 * 1000,
    ) {
        override fun toString(): String = "Spec(source=$source, description=$descriptionInternal)"
    }

    data class Result(val success: Boolean, val exception: Exception? = null)

    companion object {
        internal val TAG: String = logTag("Automation", "Crawler")
    }

    @AssistedFactory
    interface Factory {
        fun create(host: AutomationHost): StepProcessor
    }
}