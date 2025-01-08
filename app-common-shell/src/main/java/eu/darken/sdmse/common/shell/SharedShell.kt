package eu.darken.sdmse.common.shell

import eu.darken.flowshell.core.cmd.FlowCmdShell
import eu.darken.flowshell.core.process.FlowProcess
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking

class SharedShell(
    tag: String,
    scope: CoroutineScope,
) : HasSharedResource<FlowCmdShell.Session> {
    private val aTag = "$tag:SharedShell"
    private val source = callbackFlow {
        log(aTag) { "Initiating connection to host." }

        val session = try {
            val sharedSession = FlowCmdShell().session.replayingShare(this)
            sharedSession.launchIn(this + Dispatchers.IO)
            sharedSession.first()
        } catch (e: Exception) {
            throw e
        }

        invokeOnClose {
            log(aTag) { "Canceling!" }
            runBlocking {
                session.close()
            }
        }

        send(session)

        val exitCode = try {
            session.waitFor()
        } catch (sessionError: Exception) {
            throw IllegalStateException("SharedShell finished unexpectedly", sessionError)
        }

        if (exitCode != FlowProcess.ExitCode.OK) {
            throw IllegalStateException("SharedShell finished with exitcode $exitCode")
        }
    }

    val session = SharedResource(aTag, scope, source)

    override val sharedResource: SharedResource<FlowCmdShell.Session> = session

}