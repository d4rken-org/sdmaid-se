package eu.darken.sdmse.automation.core.specs

import android.view.WindowManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.automation.core.AutomationHost
import eu.darken.sdmse.automation.core.errors.AutomationOverlayException
import eu.darken.sdmse.automation.core.errors.AutomationTimeoutException
import eu.darken.sdmse.automation.core.errors.PlanAbortException
import eu.darken.sdmse.automation.core.errors.StepAbortException
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.progress.Progress
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout


class AutomationExplorer @AssistedInject constructor(
    @Assisted private val host: AutomationHost,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.Data(primary = eu.darken.sdmse.common.R.string.general_progress_preparing.toCaString())
    )

    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(50)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun process(spec: AutomationSpec.Explorer) {
        log(TAG) { "process(): ${spec.tag}" }
        val context = object : Context {

            override val progress: Flow<Progress.Data?> = this@AutomationExplorer.progress

            override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
                this@AutomationExplorer.updateProgress(update)
            }

            override val host: AutomationHost = this@AutomationExplorer.host

            override fun toString(): String = "AutomationContext(host=$host)"
        }

        log(TAG, VERBOSE) { "Creating plan..." }
        val plan = spec.createPlan()
        log(TAG) { "Plan created: $plan" }

        try {
            withTimeout(spec.executionTimeout.toMillis()) {
                var abortReplays = 0
                while (currentCoroutineContext().isActive) {
                    try {
                        plan(context)
                        // Success :)
                        return@withTimeout
                    } catch (e: PlanAbortException) {
                        log(TAG, WARN) { "ABORT Plan due to ${e.asLog()}" }
                        throw e
                    } catch (e: StepAbortException) {
                        // Stepper turns a deliberate skip into a `break`, so this should only ever be
                        // a genuine failure. Mirror its semantics anyway if one does reach us.
                        if (e.treatAsSuccess) {
                            log(TAG, WARN) { "Plan finished early, step skipped: ${e.asLog()}" }
                            return@withTimeout
                        }
                        // The step declared itself unretryable, so replaying the plan re-runs the
                        // same failing step. Allow a bounded number of replays in case the screen
                        // was merely in a bad state, but don't keep going until executionTimeout:
                        // that turns a decided failure into a 30s stall per target.
                        if (abortReplays >= spec.executionRetryCount) {
                            log(TAG, WARN) { "Step aborted unretryably, out of replays:\n${e.asLog()}" }
                            throw e
                        }
                        abortReplays++
                        log(TAG, WARN) {
                            "Step aborted unretryably, replay $abortReplays/${spec.executionRetryCount}:\n${e.asLog()}"
                        }
                        delay(300)
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Plan failed, retrying:\n${e.asLog()}" }
                        delay(300)
                    }
                }
            }
        } catch (e: Exception) {
            when (e) {
                is TimeoutCancellationException -> {
                    log(TAG, WARN) { "Automation timed out: $e" }
                    throw AutomationTimeoutException(e)
                }

                is WindowManager.BadTokenException -> {
                    log(TAG, ERROR) { "Couldn't add overlay: $e" }
                    throw AutomationOverlayException(e)
                }

                else -> throw e
            }
        }
    }

    interface Context : Progress.Host, Progress.Client {

        val host: AutomationHost

        val androidContext: android.content.Context
            get() = host.service
    }

    companion object {
        internal val TAG: String = logTag("Automation", "Explorer")
    }

    @AssistedFactory
    interface Factory {
        fun create(host: AutomationHost): AutomationExplorer
    }
}