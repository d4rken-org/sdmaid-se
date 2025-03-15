package eu.darken.sdmse.common.pkgs.pkgops

import dagger.Reusable
import eu.darken.flowshell.core.cmd.FlowCmd
import eu.darken.flowshell.core.cmd.execute
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.shell.SharedShell
import eu.darken.sdmse.common.user.UserHandle2
import javax.inject.Inject

@Reusable
class ProcessScanner @Inject constructor(
    private val sharedShell: SharedShell,
) {

    suspend fun getRunningPackages(): List<PkgProcess> = sharedShell.useRes { shell ->
        val result = FlowCmd("ps -A | grep -E 'u[0-9]+_a[0-9]+'").execute(shell)
        if (!result.isSuccessful) {
            log(TAG, ERROR) { "Command failed: $result" }
            throw IllegalArgumentException("Non-OK command result! ")
        }

        parse(result)
    }

    private fun parse(result: FlowCmd.Result): List<PkgProcess> {
        val header = result.output.firstOrNull()?.trim()?.split("\\s+".toRegex())
        if (header == null) {
            log(TAG, ERROR) { "Output has no header: $result" }
            return emptyList()
        }

        val pidIndex = header.indexOf("PID").takeIf { it >= 0 } ?: 1
        val ppidIndex = header.indexOf("PPID").takeIf { it >= 0 } ?: 2
        val vszIndex = header.indexOf("VSZ").takeIf { it >= 0 } ?: 3
        val rssIndex = header.indexOf("RSS").takeIf { it >= 0 } ?: 4
        val stateIndex = header.indexOf("S").takeIf { it >= 0 } ?: 7

        val processes = result.output
            .drop(1)
            .mapNotNull { line ->
                val parts = line.trim().split(columnRegex)
                if (parts.size < 8 || !parts[0].matches(rowRegex)) {
                    log(TAG, WARN) { "Unexpected row: $line" }
                    return@mapNotNull null
                }
                PkgProcess(
                    handle = handleRegex.find(parts[0])
                        ?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?.let { UserHandle2(handleId = it) }
                        ?: return@mapNotNull null,
                    pkgId = parts.lastOrNull()?.toPkgId() ?: return@mapNotNull null,
                    pid = parts.getOrNull(pidIndex)?.toIntOrNull(),
                    ppid = parts.getOrNull(ppidIndex)?.toIntOrNull(),
                    vsz = parts.getOrNull(vszIndex)?.toLongOrNull(),
                    rss = parts.getOrNull(rssIndex)?.toLongOrNull(),
                    wchan = parts.getOrNull(5),
                    pc = parts.getOrNull(6),
                    state = parts.getOrNull(stateIndex),
                )
            }

        if (Bugs.isTrace) log(TAG, VERBOSE) { "Parsed $result to $processes" }
        return processes
    }

    data class PkgProcess(
        val handle: UserHandle2,
        val pid: Int?,
        val ppid: Int?,
        val vsz: Long?,
        val rss: Long?,
        val wchan: String?,
        val pc: String?,
        val state: String?,
        val pkgId: Pkg.Id,
    )

    companion object {
        private val handleRegex = Regex("u(\\d+)_a\\d+")
        private val rowRegex = Regex("u[0-9]+_a[0-9]+")
        private val columnRegex = "\\s+".toRegex()
        val TAG = logTag("Pkg", "Ops", "RunningProcess", "Scanner", Bugs.processTag)
    }
}