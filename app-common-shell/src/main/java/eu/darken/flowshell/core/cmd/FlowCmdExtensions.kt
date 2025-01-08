package eu.darken.flowshell.core.cmd

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlin.time.Duration

suspend fun FlowCmd.execute(
    cmdShell: FlowCmdShell = FlowCmdShell()
): FlowCmd.Result = cmdShell.session
    .map { it.execute(this) }
    .first()

suspend fun FlowCmd.execute(session: FlowCmdShell.Session) = session.execute(this)

suspend fun FlowCmdShell.openSession(scope: CoroutineScope): Pair<FlowCmdShell.Session, Job> {
    val sharedSession = session.shareIn(
        scope = scope,
        replay = 1,
        started = SharingStarted.WhileSubscribed(replayExpiration = Duration.ZERO)
    )
    val job = sharedSession.launchIn(scope)
    return sharedSession.first() to job
}

val FlowCmd.Result.exception: Throwable?
    get() = if (isSuccessful) null else FlowCmdShellException(
        message = "Command failed: exitCode=$exitCode:${errors.joinToString("\n")}",
    )