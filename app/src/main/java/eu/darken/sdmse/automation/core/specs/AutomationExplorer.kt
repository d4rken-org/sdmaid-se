package eu.darken.sdmse.automation.core.specs

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.automation.core.AutomationHost
import eu.darken.sdmse.automation.core.common.StepProcessor
import eu.darken.sdmse.automation.core.errors.PlanAbortException
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout


class AutomationExplorer @AssistedInject constructor(
    @Assisted private val host: AutomationHost,
    private val stepProcessorFactory: StepProcessor.Factory,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.Data(primary = R.string.general_progress_preparing.toCaString())
    )

    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(50)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun process(spec: AutomationSpec.Explorer) {
        log(TAG) { "process(): ${spec.tag}" }

        var attempts = 0

        val context = object : Context {

            override val progress: Flow<Progress.Data?> = this@AutomationExplorer.progress

            override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
                this@AutomationExplorer.updateProgress(update)
            }

            override val attempts: Int
                get() = attempts

            override val host: AutomationHost = this@AutomationExplorer.host

            override val stepper: StepProcessor = stepProcessorFactory.create(host)
        }

        log(TAG, VERBOSE) { "Creating plan..." }
        val plan = spec.createPlan()
        log(TAG) { "Plan created: $plan" }

        withTimeout(spec.executionTimeout.toMillis()) {
            while (currentCoroutineContext().isActive) {
                try {
                    plan(context)
                    // Success :)
                    return@withTimeout
                } catch (e: PlanAbortException) {
                    log(TAG, WARN) { "ABORT Plan due to ${e.asLog()}" }
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "Plan failed, retrying (attempts=$attempts):\n${e.asLog()}" }
                    delay(300)
                    attempts++
                }
            }
        }
    }

    interface Context : Progress.Host, Progress.Client {

        val attempts: Int

        val host: AutomationHost

        val androidContext: android.content.Context
            get() = host.service

        val stepper: StepProcessor
    }

    companion object {
        internal val TAG: String = logTag("Automation", "Explorer")
    }

    @AssistedFactory
    interface Factory {
        fun create(host: AutomationHost): AutomationExplorer
    }
}