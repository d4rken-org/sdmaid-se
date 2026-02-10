package eu.darken.sdmse.automation.core

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.automation.core.animation.AnimationState
import eu.darken.sdmse.automation.core.animation.AnimationTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.updateProgressPrimary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AutomationProcessor @AssistedInject constructor(
    @Assisted private val automationHost: AutomationHost,
    private val dispatcherProvider: DispatcherProvider,
    private val moduleFactories: Set<@JvmSuppressWildcards AutomationModule.Factory>,
    private val animationTool: AnimationTool,
) {
    private val execLock = Mutex()

    var hasTask: Boolean = false
        private set

    suspend fun process(task: AutomationTask): AutomationTask.Result = execLock.withLock {
        hasTask = true
        log(TAG) { "process(): $task" }
        automationHost.updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)

        val factory: AutomationModule.Factory = moduleFactories.singleOrNull { it.isResponsible(task) }
            ?: throw IllegalStateException("No module found for $task")

        val moduleScope = CoroutineScope(dispatcherProvider.IO + SupervisorJob())

        val module = factory.create(automationHost, moduleScope)

        try {
            animationTool.restorePendingState()
        } catch (e: Exception) {
            log(TAG, WARN) { "process(): Failed to restore pending animation state: ${e.asLog()}" }
        }

        var prevAnimState: AnimationState? = null

        val result = try {
            if (animationTool.canChangeState()) {
                log(TAG) { "process(): Disabling animations" }
                prevAnimState = animationTool.getState()
                animationTool.persistPendingState(prevAnimState)
                animationTool.setState(AnimationState.DISABLED)
            }

            log(TAG, INFO) { "process(): Current animation state: ${animationTool.getState()}" }

            log(TAG, VERBOSE) { "process(): Processing $task via $module" }
            withContext(dispatcherProvider.IO) {
                module.process(task)
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "process(): Task failed: $task\n${e.asLog()}" }
            throw e
        } finally {
            if (prevAnimState != null) {
                log(TAG) { "process(): Restoring previous animation state" }
                withContext(NonCancellable) {
                    var restored = false
                    try {
                        animationTool.setState(prevAnimState)
                        restored = true
                    } catch (e: Exception) {
                        log(TAG, ERROR) { "process(): Failed to restore animation state: ${e.asLog()}" }
                    }
                    if (restored) {
                        try {
                            animationTool.clearPendingState()
                        } catch (e: Exception) {
                            log(TAG, ERROR) { "process(): Failed to clear pending state: ${e.asLog()}" }
                        }
                    }
                }
            }
            log(TAG, VERBOSE) { "process(): Canceling module scope..." }
            moduleScope.cancel()
            hasTask = false
        }

        log(TAG) { "process(): Result is $result" }

        result
    }

    @AssistedFactory
    interface Factory {
        fun create(host: AutomationHost): AutomationProcessor
    }

    companion object {
        val TAG: String = logTag("Automation", "Service", "Processor")
    }
}
