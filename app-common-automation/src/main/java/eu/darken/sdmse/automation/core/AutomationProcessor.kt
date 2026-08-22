package eu.darken.sdmse.automation.core

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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

        var moduleScope: CoroutineScope? = null

        try {
            log(TAG) { "process(): $task" }
            automationHost.updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)

            val factory: AutomationModule.Factory = moduleFactories.singleOrNull { it.isResponsible(task) }
                ?: throw IllegalStateException("No module found for $task")

            val scope = CoroutineScope(dispatcherProvider.IO + SupervisorJob())
            moduleScope = scope

            val module = factory.create(automationHost, scope)

            val restoreResult = try {
                animationTool.restorePendingState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "process(): Failed to restore pending animation state: ${e.asLog()}" }
                AnimationTool.RestoreResult.FAILED
            }

            val runModule: suspend () -> AutomationTask.Result = {
                log(TAG, INFO) { "process(): Current animation state: ${animationTool.getState()}" }
                log(TAG, VERBOSE) { "process(): Processing $task via $module" }
                withContext(dispatcherProvider.IO) { module.process(task) }
            }

            val result = try {
                when {
                    restoreResult == AnimationTool.RestoreResult.FAILED -> {
                        log(TAG, WARN) { "process(): Not touching animations, a pending restore is still outstanding" }
                        runModule()
                    }

                    animationTool.canChangeState() -> {
                        log(TAG) { "process(): Disabling animations" }
                        animationTool.withAnimationsDisabled { runModule() }
                    }

                    else -> runModule()
                }
            } catch (e: CancellationException) {
                log(TAG, INFO) { "process(): Task cancelled: $task ($e)" }
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "process(): Task failed: $task\n${e.asLog()}" }
                throw e
            }

            log(TAG) { "process(): Result is $result" }

            result
        } finally {
            log(TAG, VERBOSE) { "process(): Canceling module scope..." }
            moduleScope?.cancel()
            hasTask = false
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(host: AutomationHost): AutomationProcessor
    }

    companion object {
        val TAG: String = logTag("Automation", "Service", "Processor")
    }
}
