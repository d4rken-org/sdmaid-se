package eu.darken.sdmse.automation.core.specs

import java.time.Duration

sealed interface AutomationSpec {
    interface Explorer : AutomationSpec {

        val executionTimeout: Duration
            get() = Duration.ofSeconds(20)

        val executionRetryCount: Int
            get() = 3

        suspend fun createPlan(): suspend (AutomationExplorer.Context) -> Unit

    }
}