package eu.darken.sdmse.automation.core

import android.accessibilityservice.AccessibilityService
import android.content.Context
import eu.darken.sdmse.common.progress.Progress
import kotlinx.coroutines.CoroutineScope

abstract class AutomationModule(
    val host: AutomationHost
) : Progress.Client {

    val context: Context get() = host.service

    val service: AccessibilityService get() = host.service

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        host.updateProgress(update)
    }

    abstract suspend fun process(task: AutomationTask): AutomationTask.Result

    interface Factory {
        fun isResponsible(task: AutomationTask): Boolean
        fun create(
            host: AutomationHost,
            moduleScope: CoroutineScope,
        ): AutomationModule
    }

}