package eu.darken.sdmse.common.ipc

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface IpcClientModule {

    @OptIn(ExperimentalEncodingApi::class)
    fun String.decodeStacktrace(): Array<StackTraceElement>? = try {
        val decodedBytes = Base64.decode(this)
        ObjectInputStream(ByteArrayInputStream(decodedBytes)).use {
            it.readObject() as Array<StackTraceElement>
        }
    } catch (e: Exception) {
        null
    }

    fun Throwable.unwrapPropagation(): Throwable {
        val matchResult = Regex("^([a-zA-Z0-9.]+Exception): ").find((message ?: ""))
        val exceptionName = matchResult?.groupValues?.get(1) ?: return this
        val messageParts = message!!
            .removePrefix(matchResult.groupValues.first())
            .trim()
            .split(IpcHostModule.STACK_MARKER)

        return try {
            Class.forName(exceptionName)
                .asSubclass(Throwable::class.java)
                .getConstructor(String::class.java)
                .newInstance(messageParts.first())
                .also { newException ->
                    //  TODO: Couldn't find a way to keep the trace through parceling
                    // it.stackTrace = this.stackTrace
                    if (Bugs.isDebug && messageParts.size > 1) {
                        log(VERBOSE) { "Decoding stacktrace..." }
                        messageParts[1].decodeStacktrace()?.let { remoteTrace ->
                            // Stacktrace on this side of the binder + the stacktrace on the other side of it
                            newException.stackTrace = (remoteTrace + stackTrace).filter {
                                !it.className.startsWith("android.os.Binder") && !it.className.startsWith("android.os.Parcel")
                            }.toTypedArray()
                        }
                    }
                }
        } catch (e: Exception) {
            log(WARN) { "Failed to unwrap exception: $this" }
            this
        }
    }

}