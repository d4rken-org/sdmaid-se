package eu.darken.sdmse.common

import android.os.Build
import eu.darken.rxshell.cmd.Cmd
import eu.darken.rxshell.cmd.RxCmdShell
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.useRootNow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

// https://github.com/d4rken/sdmaid-public/issues/5331
// https://github.com/d4rken/sdmaid-public/issues/5201
@Singleton
class MountMaster @Inject constructor(
    private val rootManager: RootManager,
) {
    private val lock = Mutex()
    private var cached: Boolean? = null

    suspend fun isMountMasterAvailable(): Boolean = lock.withLock {
        cached?.let { return@withLock it }
        determineStatus().also { cached = it }
    }

    private suspend fun determineStatus(): Boolean {
        if (!rootManager.useRootNow()) {
            log(TAG, INFO) { "We are not rooted, no need for mount-master." }
            return false
        }
        if (!hasApiLevel(Build.VERSION_CODES.R)) {
            log(TAG, INFO) { "Api level is below Android 11 mount-master is not required." }
            return false
        }

        log(TAG) { "Checking for mount-master support..." }
        val result = Cmd.builder("su --help").execute(RxCmdShell.builder().root(true).build())
        if (result.exitCode != Cmd.ExitCode.OK) {
            log(TAG, INFO) { "mount-master check failed: ${result.merge()}" }
            return false
        }

        val supported = result.merge().any { it.contains("--mount-master") }
        log(TAG, INFO) { "mount-master is required and current support status is supported=$supported" }
        return supported
    }

    companion object {
        private val TAG = logTag("MountMaster")
    }
}