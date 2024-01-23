package eu.darken.flowshell.core.process

import eu.darken.flowshell.core.FlowShellDebug
import eu.darken.rxshell.extra.ApiWrap
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.plus
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.util.regex.Pattern

private const val TAG = "FS:FlowProcess:Extensions"
private val PID_PATTERN = Pattern.compile("^.+?pid=(\\d+).+?$")
private val SPACES_PATTERN = Pattern.compile("\\s+")

internal val Process.isAlive2: Boolean
    get() = if (ApiWrap.hasOreo()) {
        @Suppress("NewApi")
        this.isAlive
    } else {
        try {
            this.exitValue()
            false
        } catch (e: IllegalThreadStateException) {
            true
        }
    }

// stupid method for getting the pid, but it actually works
internal val Process.processPid: Int?
    get() = PID_PATTERN.matcher(this.toString())
        .takeIf { it.matches() }
        ?.let { it.group(1).toInt() }

suspend fun Process.killWithRootPid(): Boolean {
    if (FlowShellDebug.isDebug) log(TAG) { "kill($this)" }
    if (!this.isAlive) {
        if (FlowShellDebug.isDebug) log(TAG) { "Process is no longer alive, skipping kill." }
        return true
    }

    val pid = this.processPid
    if (pid == null) {
        if (FlowShellDebug.isDebug) log(TAG, ERROR) { "Can't find PID for $this" }
        return false
    }
    val pidFamily = this.pidFamily()
    if (FlowShellDebug.isDebug) log(TAG) { "Pid family pids: $pidFamily" }

    return pidFamily?.destroyPids() ?: false
}

// use 'ps' to get this pid and all pids that are related to it (e.g. spawned by it)
internal fun Process.pidFamily(): PidFamily? {
    val parentPid = processPid ?: return null
    var process: Process? = null
    val childPids = try {
        process = processFactory!!.start("su")
        val os = OutputStreamWriter(process.outputStream)
        val errorsHarvester = makeMiniHarvester(process.errorStream)
        errorsHarvester.subscribe()
        val outputHarvester = makeMiniHarvester(process.inputStream)
        outputHarvester.subscribe()
        os.write("ps" + LineReader.getLineSeparator())
        os.write("exit" + LineReader.getLineSeparator())
        os.flush()
        os.close()
        val exitcode = process.waitFor()
        errorsHarvester.blockingGet()
        val output = outputHarvester.blockingGet()
        if (FlowShellDebug.isDebug) Timber.tag(TAG).d("getAllPids() exitcode: %d", exitcode)
        if (exitcode == RxProcess.ExitCode.OK) {
            for (s in output) {
                if (output.indexOf(s) == 0) continue  // SKIP title row
                val line = SPACES_PATTERN.split(s)
                if (line.size >= 3) {
                    try {
                        if (parentPid == line[2].toInt()) result.add(line[1].toInt())
                    } catch (e: NumberFormatException) {
                        Timber.tag(TAG).w(e, "getAllPids(parentPid=%d) parse failure: %s", parentPid, line)
                    }
                }
            }
        }
    } catch (interrupt: InterruptedException) {
        Timber.tag(TAG).w(interrupt, "Interrupted!")
        return null
    } catch (e: IOException) {
        Timber.tag(TAG).w(e, "IOException, IOException, pipe broke?")
        return null
    } finally {
        process?.destroy()
    }

    return PidFamily(parentPid, childPids)
}

internal data class PidFamily(
    val parent: Int,
    val children: List<Int>
) {
    suspend fun destroyPids(): Boolean {
        val pids = listOf(parent) + children
        var process: Process? = null
        return try {
            process = ProcessBuilder("su").start()
            val out = OutputStreamWriter(process.outputStream)
            coroutineScope {
                process.errorStream.miniHarvester().launchIn(this + Dispatchers.IO)
                process.inputStream.miniHarvester().launchIn(this + Dispatchers.IO)
                for (p in children) out.write("kill $p${System.lineSeparator()}")
                out.write("exit${System.lineSeparator()}")
                out.flush()
                out.close()
                val exitcode = process.waitFor()
                if (FlowShellDebug.isDebug) log(TAG) { "destroyPids(pids=$pids) exitcode: $exitcode" }
                exitcode == 0
            }
        } catch (interrupt: InterruptedException) {
            log(TAG, WARN) { "destroyPids(pids=$pids) Interrupted!" }
            false
        } catch (e: IOException) {
            log(TAG, WARN) { "destroyPids(pids=$pids) IOException, command failed? not found?" }
            false
        } finally {
            process?.destroy()
        }
        return true
    }
}

fun InputStream.miniHarvester() = callbackFlow<String> {
    return Observable
        .create<String?>(ObservableOnSubscribe<String?> { emitter: ObservableEmitter<String?> ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lineReader = LineReader()
            var line: String?
            try {
                while (lineReader.readLine(reader).also { line = it } != null && !emitter.isDisposed()) {
                    emitter.onNext(line!!)
                }
            } catch (e: IOException) {
                if (FlowShellDebug.isDebug) Timber.tag(TAG).d("MiniHarvester read error: %s", e.message)
            } finally {
                emitter.onComplete()
            }
        })
        .doOnEach(Consumer<Notification<String?>> { n: Notification<String?>? ->
            if (FlowShellDebug.isDebug) Timber.tag(
                TAG
            ).v("miniHarvesters:doOnEach %s", n)
        })
        .subscribeOn(Schedulers.io())
        .toList()
        .onErrorReturnItem(ArrayList<String>())
        .cache()
}
