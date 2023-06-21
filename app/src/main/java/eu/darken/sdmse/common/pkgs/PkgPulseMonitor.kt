package eu.darken.sdmse.common.pkgs

import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.root.ShellOpsCmd
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PkgPulseMonitor @Inject constructor(
    private val shellOps: ShellOps,
) {

    suspend fun isRunning(id: Installed.InstallId): Boolean {
        val cmd = ShellOpsCmd("pidof ${id.pkgId.name}")
        val result = shellOps.execute(cmd, ShellOps.Mode.ELEVATED)
        log(TAG) { "isRunning($id): $cmd -> $result" }
        return result.isSuccess
    }

    companion object {
        private val TAG = logTag("PkgPulseMonitor")
    }
}