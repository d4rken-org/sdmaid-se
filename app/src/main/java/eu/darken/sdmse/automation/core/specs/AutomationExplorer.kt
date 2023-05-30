package eu.darken.sdmse.automation.core.specs

import android.accessibilityservice.AccessibilityService
import android.content.res.Resources
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.automation.core.AutomationHost
import eu.darken.sdmse.automation.core.common.StepProcessor
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.progress.Progress
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import java.util.Locale


class AutomationExplorer @AssistedInject constructor(
    @Assisted private val host: AutomationHost,
    private val stepProcessorFactory: StepProcessor.Factory,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.DEFAULT_STATE.copy(primary = R.string.general_progress_preparing.toCaString())
    )

    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(50)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun process(spec: AutomationSpec.Explorer): Unit {
        log(TAG) { "process(): $spec" }

        var attempts = 0

        val context = object : Context {

            override val progress: Flow<Progress.Data?> = this@AutomationExplorer.progress

            override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
                this@AutomationExplorer.updateProgress(update)
            }

            override val attempts: Int
                get() = attempts

            override val service: AccessibilityService = host.service

            override val stepper: StepProcessor = stepProcessorFactory.create(host)
        }

        withTimeout(spec.executionTimeout.toMillis()) {
            log(TAG, VERBOSE) { "Creating plan..." }
            val plan = spec.createPlan()
            log(TAG) { "Plan created: $plan" }

            while (currentCoroutineContext().isActive) {
                try {
                    plan(context)
                    // Success :)
                    return@withTimeout
                } catch (e: Exception) {
                    log(TAG, WARN) { "Plan execution failed ($context) failed:\n${e.asLog()}" }
                    attempts++
                    delay(300)
                }
            }
        }
    }

    interface Context : Progress.Host, Progress.Client {

        val attempts: Int

        val service: AccessibilityService

        val stepper: StepProcessor

        fun getSysLocale(): Locale = if (hasApiLevel(24)) {
            @Suppress("NewApi")
            Resources.getSystem().configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            Resources.getSystem().configuration.locale
        }
    }

    companion object {
        internal val TAG: String = logTag("Automation", "Explorer")
    }

    @AssistedFactory
    interface Factory {
        fun create(host: AutomationHost): AutomationExplorer
    }
}