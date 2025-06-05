package eu.darken.sdmse.common.ipc

import android.os.DeadObjectException
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface IpcClientModule {

    @OptIn(ExperimentalEncodingApi::class)
    fun String.decodeStacktrace(): Array<StackTraceElement>? = try {
        val decodedBytes = Base64.decode(this)
        ObjectInputStream(ByteArrayInputStream(decodedBytes)).use {
            @Suppress("UNCHECKED_CAST")
            it.readObject() as Array<StackTraceElement>
        }
    } catch (_: Exception) {
        null
    }

    fun Throwable.refineException(): Throwable = when (this) {
        is DeadObjectException -> ServiceConnectionLostException(this)
        else -> unwrapPropagation()
    }

    fun Throwable.unwrapPropagation(): Throwable {
        val matchResult = Regex("^([a-zA-Z0-9$.]+Exception): ").find((message ?: ""))
        val exceptionName = matchResult?.groupValues?.get(1)
        if (exceptionName == null) {
            log(TAG, WARN) { "Couldn't unwrap exception, it didn't match: $this" }
            return this
        }
        val messageParts = message!!
            .removePrefix(matchResult.groupValues.first())
            .split(IpcHostModule.STACK_MARKER)
            .map { it.trim() }

        val unwrappedException = try {
            Class.forName(exceptionName)
                .asSubclass(Throwable::class.java)
                .getConstructor(String::class.java)
                .newInstance(messageParts.first())
                .also { newException ->
                    //  TODO: Couldn't find a way to keep the trace through parceling
                    if (Bugs.isDebug && messageParts.size > 1) {
                        log(TAG, VERBOSE) { "Decoding stacktrace..." }
                        messageParts[1].decodeStacktrace()?.let { remoteTrace ->
                            // Stacktrace on this side of the binder + the stacktrace on the other side of it
                            newException.stackTrace = (remoteTrace + stackTrace).filter {
                                !it.className.startsWith("android.os.Binder") && !it.className.startsWith("android.os.Parcel")
                            }.toTypedArray()
                        }
                    }
                }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to unwrap exception:\n---\n$this\n---\n${e.asLog()}" }
            UnwrappedIPCException(this.toString())
        }

        log(TAG, VERBOSE) { "Propagating unwrapped exception: $unwrappedException" }
        return unwrappedException
    }

    companion object {
        private val TAG = logTag("IPC", "Module")
    }

}