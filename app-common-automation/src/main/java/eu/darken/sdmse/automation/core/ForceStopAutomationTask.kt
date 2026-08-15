package eu.darken.sdmse.automation.core

import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.toPkgId

class ForceStopAutomationTask(
    val targets: List<InstallId>,
    // An explicit user decision for a specific app may target packages that automated
    // (multi-app) force-stops must skip, e.g. System UI.
    val allowOffLimits: Boolean = false,
) : AutomationTask {

    data class Result(
        val successful: Collection<InstallId>,
        val failed: Collection<InstallId>,
    ) : AutomationTask.Result

    companion object {
        /**
         * Packages that must never be force-stopped by us.
         *
         * Force-stopping System UI cancels the PendingIntents it registered for accessibility
         * system actions (BACK/HOME/DPAD), while its persistent process keeps running and never
         * re-registers them. All performGlobalAction calls then fail until System UI restarts
         * (usually via reboot). Force-stopping ourselves kills our own accessibility service
         * mid-run.
         */
        // BuildConfigWrap can't initialize on the plain JVM unit test classpath (reflection on
        // BuildConfig); tolerate that so production code using this filter stays JVM-testable.
        val OFF_LIMIT_PKGS: Set<Pkg.Id> by lazy {
            setOfNotNull(
                "com.android.systemui".toPkgId(),
                runCatching { BuildConfigWrap.APPLICATION_ID.toPkgId() }.getOrNull(),
            )
        }
    }
}
