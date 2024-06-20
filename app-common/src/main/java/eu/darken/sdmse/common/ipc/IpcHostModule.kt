package eu.darken.sdmse.common.ipc

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface IpcHostModule {

    @OptIn(ExperimentalEncodingApi::class)
    fun Array<StackTraceElement>.encodeBase64(): String? = try {
        val baos = ByteArrayOutputStream()
        ObjectOutputStream(baos).use {
            it.writeObject(this)
        }
        Base64.encode(baos.toByteArray())
    } catch (e: Exception) {
        null
    }


    // Not all exception can be passed through the binder
    // See Parcel.writeException(...)
    fun Throwable.wrapToPropagate(): Exception {
        val msgBuilder = StringBuilder()
        msgBuilder.append(this.toString())
        cause?.let {
            msgBuilder.append("\nCaused by: ")
            msgBuilder.append(it.toString())
        }

        if (Bugs.isDebug) {
            log(VERBOSE) { "Encoding stacktrace..." }
            // TODO Find better way to pass trace, see IpcClientModule
            val encodedTrace = stackTrace.encodeBase64()
            if (encodedTrace != null) {
                msgBuilder.append(STACK_MARKER).append(encodedTrace)
            }
        }

        return UnsupportedOperationException(msgBuilder.toString())
    }

    companion object {
        const val STACK_MARKER = "\n\n#STACK#:"
    }
}