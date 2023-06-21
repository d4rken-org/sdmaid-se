package eu.darken.sdmse.common.pkgs

import android.app.usage.UsageStatsManager
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.useRootNow
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.root.ShellOpsCmd
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupModule
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PkgPulseMonitor @Inject constructor(
    private val shellOps: ShellOps,
    private val usageStatsManager: UsageStatsManager,
    private val rootManager: RootManager,
    private val usageStatsSetupModule: UsageStatsSetupModule,
) {

    suspend fun isRunning(id: Installed.InstallId): Boolean {
        var running: Boolean? = null

        if (rootManager.useRootNow()) {
            val cmd = ShellOpsCmd("pidof ${id.pkgId.name}")
            val result = shellOps.execute(cmd, ShellOps.Mode.ELEVATED)
            log(TAG) { "isRunning($id): $cmd -> $result" }
            running = result.isSuccess
        }

        if (running == null && usageStatsSetupModule.state.first().isComplete) {
            val now = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 60 * 1000, now)
            val stat = stats.find { it.packageName == id.pkgId.name }
            val secondsSinceLastUse = stat?.let { (System.currentTimeMillis() - it.lastTimeUsed) / 1000L }
            running = secondsSinceLastUse?.let { it < PULSE_PERIOD_SECONDS }
            log(TAG) { "isRunning($id): ${secondsSinceLastUse}s" }
        }

        return running ?: false
    }

    companion object {
        private const val PULSE_PERIOD_SECONDS = 10
        private val TAG = logTag("PkgPulseMonitor")
    }
}