package eu.darken.sdmse.automation.core.common

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.automation.core.AutomationHost
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.CaDrawable
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressPrimary
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout


class StepProcessor @AssistedInject constructor(
    @Assisted private val host: AutomationHost,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.DEFAULT_STATE.copy(primary = R.string.general_progress_preparing.toCaString())
    )

    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(50)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun process(step: Step): Unit = withTimeout(20 * 1000) {
        log(TAG) { "crawl(): $step" }
        updateProgressPrimary(step.label)

        var attempts = 0
        while (currentCoroutineContext().isActive) {
            try {
                withTimeout(5 * 1000) {
                    doCrawl(step, attempts++)
                }
                return@withTimeout
            } catch (e: StepAbortException) {
                log(TAG, WARN) { "ABORT Step due to ${e.asLog()}" }
                break
            } catch (e: Exception) {
                log(TAG, WARN) { "crawl(): Attempt $attempts failed on $step:\n${e.asLog()}" }
                delay(300)
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
            log(TAG, VERBOSE) { "Launching window intent: ${step.windowIntent}" }
            host.service.startActivity(step.windowIntent)
        }

        // avg delay between activity launch and acs event
        delay(200)

        // Wait for correct window
        val targetWindowRoot: AccessibilityNodeInfo = withTimeout(4000) {
            // Condition for the right window, e.g. check title
            if (step.windowIntent != null && step.windowEventFilter != null) {
                log(TAG, VERBOSE) { "Waiting for window event filter to pass..." }
                host.events.filter {
                    log(TAG, VERBOSE) { "Testing window event $it" }
                    step.windowEventFilter.invoke(it)
                }.first()
                log(TAG, VERBOSE) { "Waiting for window event filter passed!" }
            }

            var currentRoot: AccessibilityNodeInfo? = null

            while (step.windowNodeTest != null && currentCoroutineContext().isActive) {
                currentRoot = host.windowRoot().apply {
                    log(TAG, VERBOSE) { "Looking for viable window root, current nodes:" }
                    crawl().forEach { log(TAG, VERBOSE) { it.infoShort } }
                }

                if (step.windowNodeTest.invoke(currentRoot)) {
                    break
                } else {
                    log(TAG) { "Not a viable root node: $currentRoot (spec=$step)" }
                    delay(200)
                }
            }

            currentRoot ?: host.windowRoot()
        }
        log(TAG, VERBOSE) { "Current window root node is ${targetWindowRoot.toStringShort()}" }

        val targetNode: AccessibilityNodeInfo = when {
            step.nodeTest != null -> {
                var target: AccessibilityNodeInfo? = null
                var currentRootNode = targetWindowRoot

                while (currentCoroutineContext().isActive) {
                    log(TAG, VERBOSE) { "Checking current nodes:" }
                    currentRootNode.crawl().forEach { log(TAG, VERBOSE) { it.infoShort } }

                    target = currentRootNode.crawl().map { it.node }.find { step.nodeTest.invoke(it) }

                    if (target != null) {
                        log(TAG, VERBOSE) { "Target node found: $target" }
                        break
                    } else {
                        log(TAG, VERBOSE) { "Target node not found" }
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
                    currentRootNode = host.windowRoot()
                }
                target!!
            }

            else -> host.windowRoot()
        }
        log(TAG, VERBOSE) { "Target node is ${targetNode.toStringShort()}" }

        // e.g. find a clickable parent based on the target node
        val mappedNode = step.nodeMapping?.invoke(targetNode) ?: targetNode
        log(TAG, VERBOSE) { "Mapped node is ${mappedNode.toStringShort()}" }

        // Perform action, e.g. clicking a button
        log(TAG, VERBOSE) { "Performing action on $mappedNode" }
        val success = step.action?.invoke(mappedNode, attempt) ?: true

        if (success) {
            log(TAG) { "Crawl was successful :)" }
        } else {
            throw AutomationException("Action failed on $mappedNode (spec=$step)")
        }
    }

    data class Step(
        val parentTag: String,
        val label: String,
        val icon: CaDrawable? = null,
        val windowIntent: Intent? = null,
        val windowEventFilter: (suspend (node: AccessibilityEvent) -> Boolean)? = null,
        val windowNodeTest: (suspend (node: AccessibilityNodeInfo) -> Boolean)? = null,
        val nodeTest: (suspend (node: AccessibilityNodeInfo) -> Boolean)? = null,
        val nodeRecovery: (suspend (node: AccessibilityNodeInfo) -> Boolean)? = null,
        val nodeMapping: (suspend (node: AccessibilityNodeInfo) -> AccessibilityNodeInfo)? = null,
        val action: (suspend (node: AccessibilityNodeInfo, retryCount: Int) -> Boolean)? = null
    ) {

        override fun toString(): String = "Spec(parent=$parentTag, label=$label)"

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