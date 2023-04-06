package eu.darken.sdmse.common.shell

import eu.darken.rxshell.cmd.RxCmdShell
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.callbackFlow

class SharedShell constructor(
    tag: String,
    scope: CoroutineScope,
) : HasSharedResource<RxCmdShell.Session> {
    private val aTag = "$tag:SharedShell"
    private val source = callbackFlow<RxCmdShell.Session> {
        log(aTag) { "Initiating connection to host." }

        val session = try {
            RxCmdShell.builder().build().open().blockingGet()
        } catch (e: Exception) {
            throw e
        }

        invokeOnClose {
            log(aTag) { "Canceling!" }
            session.close().subscribe()

        }

        send(session)

        val end = try {
            session.waitFor().blockingGet()
        } catch (sessionError: Exception) {
            throw IllegalStateException("SharedShell finished unexpectedly", sessionError)
        }

        if (end != 0) {
            throw IllegalStateException("SharedShell finished with exitcode $end")
        }
    }

    val session = SharedResource(aTag, scope, source)

    override val sharedResource: SharedResource<RxCmdShell.Session> = session

}