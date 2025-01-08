package eu.darken.flowshell.core.process

import eu.darken.flowshell.core.FlowShellDebug.isDebug
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.util.regex.Pattern

private const val TAG = "FS:FlowProcess:Extensions"
private val PID_PATTERN = Pattern.compile("^.+?pid=(\\d+).+?$")
private val SPACES_PATTERN = Pattern.compile("\\s+")

// stupid method for getting the pid, but it actually works
internal val Process.processPid: Int?
    get() = PID_PATTERN.matcher(this.toString())
        .takeIf { it.matches() }
        ?.group(1)?.toInt()

suspend fun Process.killViaPid(shell: String = "sh"): Boolean {
    if (isDebug) log(TAG, VERBOSE) { "killViaPid($this,$shell)" }
    if (!this.isAlive) {
        if (isDebug) log(TAG, VERBOSE) { "Process is no longer alive, skipping kill." }
        return true
    }

    val pid = this.processPid
    if (pid == null) {
        if (isDebug) log(TAG, ERROR) { "Can't find PID for $this" }
        return false
    }
    val pidFamily = this.pidFamily(shell)
    if (isDebug) log(TAG, VERBOSE) { "Family pids: $pidFamily" }

    return pidFamily?.kill() ?: false
}

// use 'ps' to get this pid and all pids that are related to it (e.g. spawned by it)
internal suspend fun Process.pidFamily(shell: String = "sh"): PidFamily? = withContext(Dispatchers.IO) {
    val parentPid = processPid ?: return@withContext null
    var process: Process? = null
    val childPids = try {
        process = ProcessBuilder(shell).start()

        val rawPidLines = coroutineScope {
            val output = mutableListOf<String>()
            val error = mutableListOf<String>()
            process.errorStream.miniHarvester().onEach { output.add(it) }.launchIn(this)
            process.inputStream.miniHarvester().onEach { error.add(it) }.launchIn(this)

            OutputStreamWriter(process.outputStream).apply {
                write("ps -o pid,ppid${System.lineSeparator()}")
                write("exit${System.lineSeparator()}")
                flush()
                close()
            }

            val exitcode = process.waitFor()
            if (exitcode == 0) output + error else null
        }

        rawPidLines
            ?.asSequence()
            ?.drop(1) // Title line
            ?.map { SPACES_PATTERN.split(it) }
            ?.filter { it.size >= 3 }
            ?.filter { parentPid == it[2].toInt() }
            ?.mapNotNull { line ->
                try {
                    line[1].toInt()
                } catch (e: NumberFormatException) {
                    if (isDebug) log(TAG, WARN) { "pidFamily(parentPid) parse failure: $line" }
                    null
                }
            }
            ?.toSet()
    } catch (interrupt: InterruptedException) {
        if (isDebug) log(TAG, WARN) { "Interrupted" }
        return@withContext null
    } catch (e: IOException) {
        if (isDebug) log(TAG, WARN) { "IOException, pipe broke?" }
        return@withContext null
    } finally {
        process?.destroy()
    }

    return@withContext PidFamily(parentPid, childPids ?: emptySet()).also {
        if (isDebug) log(TAG, VERBOSE) { "pidFamily($parentPid) is $it" }
    }
}

internal data class PidFamily(
    val parent: Int,
    val children: Set<Int>
) {
    suspend fun kill(shell: String = "sh"): Boolean = withContext(Dispatchers.IO) {
        val pids = listOf(parent) + children
        var process: Process? = null
        try {
            process = ProcessBuilder(shell).start()

            coroutineScope {
                process.errorStream.miniHarvester().launchIn(this)
                process.inputStream.miniHarvester().launchIn(this)

                OutputStreamWriter(process.outputStream).apply {
                    pids.forEach { write("kill -9 $it${System.lineSeparator()}") }
                    write("exit${System.lineSeparator()}")
                    flush()
                    close()
                }
            }

            val exitcode = process.waitFor()
            if (isDebug) log(TAG, VERBOSE) { "kill(pids=$pids) -> exitcode: $exitcode" }
            exitcode == 0

        } catch (interrupt: InterruptedException) {
            log(TAG, WARN) { "kill(pids=$pids) Interrupted!" }
            false
        } catch (e: IOException) {
            log(TAG, WARN) { "kill(pids=$pids) IOException, command failed? not found?" }
            false
        } finally {
            process?.destroy()
        }
    }
}

private fun InputStream.miniHarvester() = flow {
    val reader = this@miniHarvester.reader().buffered()
    reader.lineSequence().forEach { emit(it) }
}
