package eu.darken.sdmse.automation.core.debug

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.automation.core.AutomationHost
import eu.darken.sdmse.automation.core.AutomationModule
import eu.darken.sdmse.automation.core.AutomationTask
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.main.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class DebugTaskModule @AssistedInject constructor(
    @Assisted automationHost: AutomationHost,
    @Assisted private val moduleScope: CoroutineScope,
    val ipcFunnel: IPCFunnel
) : AutomationModule(automationHost) {

    override suspend fun process(task: AutomationTask): AutomationTask.Result {
        log(TAG) { "process(): $task" }
        updateProgressPrimary("Debug: Accessibility service")
        updateProgressSecondary("Setting host options...")
        host.changeOptions { old ->
            old
                .copy(
                    showOverlay = true,
                    accessibilityServiceInfo = AccessibilityServiceInfo().apply {
                        flags = AccessibilityServiceInfo.DEFAULT or
                                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                        eventTypes = AccessibilityEvent.TYPES_ALL_MASK
                        feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                    },
                    controlPanelSubtitle = caString { "Debug module is active" }
                )
                .also { log(TAG) { "Updating options from $old to $it" } }
        }

        log(TAG) { "process(): Host options adjusted" }
        val ccTask = task as DebugTask
        val startTime = System.currentTimeMillis()

        updateProgressSecondary("Listening to events...")
        val eventJob = host.events
            .onEach {
                log(TAG) { "Event: $it" }
                val crawled = host.windowRoot().crawl(debug = true).toList()
                updateProgressSecondary("Event: ${it.eventType} (depth: ${crawled.last().level})")
            }
            .launchIn(moduleScope)

        eventJob.join()

        updateProgressSecondary("Finished!")
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)

        log(TAG) { "Debugtask finished in ${System.currentTimeMillis() - startTime}ms" }
        return DebugTask.Result(ccTask)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): AutomationModule.Factory
    }

    @AssistedFactory
    interface Factory : AutomationModule.Factory {
        override fun isResponsible(task: AutomationTask): Boolean = task is DebugTask

        override fun create(host: AutomationHost, moduleScope: CoroutineScope): DebugTaskModule
    }

    companion object {
        val TAG: String = logTag("Automation", "DebugModule")
    }
}