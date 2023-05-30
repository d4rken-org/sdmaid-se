package eu.darken.sdmse.automation.core

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.updateProgressPrimary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AutomationProcessor @AssistedInject constructor(
    @Assisted private val automationHost: AutomationHost,
    private val dispatcherProvider: DispatcherProvider,
    private val moduleFactories: Set<@JvmSuppressWildcards AutomationModule.Factory>
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

        val result = try {
            log(TAG, VERBOSE) { "process(): Processing $task via $module" }
            withContext(dispatcherProvider.IO) {
                module.process(task)
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "process(): Task failed: $task\n${e.asLog()}" }
            throw e
        } finally {
            log(TAG, VERBOSE) { "process(): Canceling module scope..." }
            moduleScope.cancel()
        }

        log(TAG) { "process(): Result is $result" }

        hasTask = false
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