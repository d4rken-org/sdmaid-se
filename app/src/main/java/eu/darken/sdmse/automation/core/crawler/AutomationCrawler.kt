package eu.darken.sdmse.automation.core.crawler

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.Installed
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout


class AutomationCrawler @AssistedInject constructor(
    @Assisted private val host: AutomationHost,
) {

    suspend fun crawl(step: Step): Unit = withTimeout(20 * 1000) {
        log(TAG) { "crawl(): $step" }
        var attempts = 0
        while (currentCoroutineContext().isActive) {
            try {
                withTimeout(5 * 1000) {
                    doCrawl(step, attempts++)
                }
                return@withTimeout
            } catch (e: BranchException) {
                log(TAG) { "Branching! ${e.asLog()}" }
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "crawl(): Attempt $attempts failed on $step:\n${e.asLog()}" }
                delay(300)
            }
        }
    }


    private suspend fun doCrawl(step: Step, attempt: Int = 0) {
        log(TAG, VERBOSE) { "doCrawl(): Attempt $attempt for $step" }

        log(TAG, VERBOSE) { "Looking for window root (intent=${step.windowIntent})." }

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

        if (step.windowIntent != null) host.service.startActivity(step.windowIntent)

        // avg delay between activity launch and acs event
        delay(200)

        // Wait for correct window
        val rootNode: AccessibilityNodeInfo = withTimeout(4000) {
            // Condition for the right window, e.g. check title
            if (step.windowIntent != null && step.windowEventFilter != null) {
                log(TAG, VERBOSE) { "Waiting for window event filter to pass..." }
                host.events.filter { step.windowEventFilter.invoke(it) }.first()
                log(TAG, VERBOSE) { "Waiting for window event filter passed!" }
            }

            var currentRoot: AccessibilityNodeInfo? = null
            while (currentCoroutineContext().isActive) {
                currentRoot = host.windowRoot()

                log(TAG, VERBOSE) { "Current node hierarchy:" }
                currentRoot.crawl().forEach { log(TAG, VERBOSE) { it.infoShort } }

                try {
                    if (step.windowNodeTest != null && !step.windowNodeTest.invoke(currentRoot)) {
                        log(TAG) { "Not a viable root node: $currentRoot (spec=$step)" }
                        delay(200)
                    } else {
                        break
                    }
                } catch (e: BranchException) {
                    log(TAG) { "Branching! ${e.asLog()}" }
                    throw e
                }
            }

            currentRoot ?: throw IllegalStateException("No valid root node found")
        }
        log(TAG, VERBOSE) { "Root node is ${rootNode.toStringShort()}" }

        val targetNode: AccessibilityNodeInfo = when {
            step.nodeTest != null -> {
                var target: AccessibilityNodeInfo? = null
                while (target == null) {
                    target = rootNode.crawl().map { it.node }.find { step.nodeTest.invoke(it) }

                    if (target != null) {
                        log(TAG, VERBOSE) { "Node target found: $target" }
                        break
                    }

                    if (step.nodeRecovery != null) {
                        // Should we care about whether the recovery thinks it was successful?
                        step.nodeRecovery.invoke(rootNode)
                        target = host.windowRoot().crawl().map { it.node }.find { step.nodeTest.invoke(it) }
                        delay(200)
                    } else {
                        // Timeout will hit here and cancel if necessary
                        delay(100)
                    }
                }
                target!!
            }

            else -> rootNode
        }
        log(TAG, VERBOSE) { "Target node is ${targetNode.toStringShort()}" }

        // e.g. find a clickable parent based on the target node
        val mappedNode = step.nodeMapping?.invoke(targetNode) ?: targetNode
        log(TAG, VERBOSE) { "Mapped node is ${mappedNode.toStringShort()}" }

        // Perform action, e.g. clicking a button
        val success = step.action?.invoke(mappedNode, attempt) ?: true

        if (success) {
            log(TAG) { "Crawl was successful :)" }
        } else {
            throw CrawlerException("Action failed on $mappedNode (spec=$step)")
        }
    }

    data class Step(
        val parentTag: String,
        val pkgInfo: Installed,
        val label: String,
        val windowIntent: Intent? = null,
        val windowEventFilter: (suspend ((node: AccessibilityEvent) -> Boolean))? = null,
        val windowNodeTest: (suspend ((node: AccessibilityNodeInfo) -> Boolean))? = null,
        val nodeTest: (suspend (node: AccessibilityNodeInfo) -> Boolean)? = null,
        val nodeRecovery: (suspend ((node: AccessibilityNodeInfo) -> Boolean))? = null,
        val nodeMapping: (suspend ((node: AccessibilityNodeInfo) -> AccessibilityNodeInfo))? = null,
        val action: (suspend ((node: AccessibilityNodeInfo, retryCount: Int) -> Boolean))? = null
    ) {

        override fun toString(): String = "Spec(parent=$parentTag, label=$label, pkg=${pkgInfo.packageName})"

    }

    data class Result(val success: Boolean, val exception: Exception? = null)

    companion object {
        internal val TAG: String = logTag("Automation", "Crawler")
    }

    @AssistedFactory
    interface Factory {
        fun create(host: AutomationHost): AutomationCrawler
    }
}