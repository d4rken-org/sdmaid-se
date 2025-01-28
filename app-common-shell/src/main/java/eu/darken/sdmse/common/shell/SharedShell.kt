package eu.darken.sdmse.common.shell

import eu.darken.flowshell.core.cmd.FlowCmdShell
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class SharedShell(
    tag: String,
    scope: CoroutineScope,
) : HasSharedResource<FlowCmdShell.Session> {
    private val aTag = "$tag:SharedShell"
    private val source = callbackFlow {
        log(aTag) { "Launching SharedShell session" }

        invokeOnClose { log(aTag) { "Shared shell invokeOnClose!" } }

        val session = try {
            val sharedSession = FlowCmdShell().session.replayingShare(this)
            sharedSession.launchIn(this + Dispatchers.IO)
            log(aTag, VERBOSE) { "Shared shell is launched" }
            sharedSession.first()
        } catch (e: Exception) {
            throw e
        }

        log(aTag, VERBOSE) { "Sending obtained session instance: $session" }
        send(session)

        log(aTag) { "Shared shell is open, waiting until close" }
        session.waitFor()
        awaitClose {
            log(aTag) { "Closing!" }
            runBlocking {
                try {
                    withTimeoutOrNull(5 * 1000) { session.close() } ?: session.cancel()
                    val exitCode = session.waitFor()
                    log(aTag) { "FlowCmdShell finished with exitcode $exitCode" }
                } catch (e: CancellationException) {
                    log(aTag) { "FlowCmdShell was cancelled: $e" }
                } catch (e: Exception) {
                    log(aTag, WARN) { "Session.close() failed: $e" }
                }
            }
        }
    }

    val session = SharedResource(aTag, scope, source)

    override val sharedResource: SharedResource<FlowCmdShell.Session> = session

}