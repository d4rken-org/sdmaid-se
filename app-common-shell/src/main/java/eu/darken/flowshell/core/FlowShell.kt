package eu.darken.flowshell.core

import eu.darken.flowshell.core.FlowShellDebug.isDebug
import eu.darken.flowshell.core.process.FlowProcess
import eu.darken.flowshell.core.process.killViaPid
import eu.darken.rxshell.shell.LineReader
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.stream.consumeAsFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class FlowShell(
    val shell: String = "sh",
) {
    private val process = FlowProcess(
        launch = { ProcessBuilder(shell).start() },
        kill = { it.killViaPid(shell) }
    )
    private val shellCreator = process.session
        .map { processSession ->
            if (isDebug) log(TAG, VERBOSE) { "Wrapping to shell session..." }
            Session(
                session = processSession
            )
        }
        .onEach { if (isDebug) log(TAG, VERBOSE) { "Emitting $it" } }
        .onCompletion { if (isDebug) log(TAG, VERBOSE) { "Flow is complete. ${it?.asLog()}" } }

    val session: Flow<Session> = shellCreator

    data class Session(
        private val session: FlowProcess.Session,
    ) {
        val exitCode: Flow<ExitCode>
            get() = session.exitCode.map { ExitCode((it)) }

        private val writer by lazy {
            OutputStreamWriter(session.input, StandardCharsets.UTF_8)
        }

        private fun InputStream.lineHarvester(tag: String) = flow {
            if (isDebug) log(TAG, VERBOSE) { "Harverster($tag) is active" }

            bufferedReader().use { reader ->
                reader.lines().consumeAsFlow().collect {
                    if (isDebug) log(TAG, VERBOSE) { "Harverster($tag) -> $it" }
                    emit(it)
                }
            }
            if (isDebug) log(TAG) { "Harverster($tag) is finished" }
        }.flowOn(Dispatchers.IO)

        val output: Flow<String> = session.output!!.lineHarvester("output")

        val error: Flow<String> = session.errors!!.lineHarvester("error")

        suspend fun write(line: String, flush: Boolean = true) = withContext(Dispatchers.IO) {
            if (isDebug) log(TAG) { "write(line=$line, flush=$flush)" }
            writer.write(line + LineReader.getLineSeparator())
            if (flush) writer.flush()
        }

        suspend fun waitFor(): ExitCode = withContext(Dispatchers.IO) {
            exitCode.first()
        }

        suspend fun close() = withContext(Dispatchers.IO) {
            if (isDebug) log(TAG) { "close()" }
            write("exit")
        }

        suspend fun kill() = withContext(Dispatchers.IO) {
            if (isDebug) log(TAG) { "kill()" }
            writer.close()
            session.kill()
        }
    }


    data class ExitCode(val value: Int) {
        companion object {
            val OK = ExitCode(0)
            val PROBLEM = ExitCode(1)
            val OUT_OF_RANGE = ExitCode(255)
        }
    }

    companion object {
        private const val TAG = "FS:FlowShell"
    }
}