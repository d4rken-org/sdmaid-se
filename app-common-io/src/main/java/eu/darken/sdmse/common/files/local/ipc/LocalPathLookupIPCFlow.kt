package eu.darken.sdmse.common.files.local.ipc

import android.os.Parcel
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.flow.chunked
import eu.darken.sdmse.common.ipc.RemoteInputStream
import eu.darken.sdmse.common.ipc.inputStream
import eu.darken.sdmse.common.ipc.remoteInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream


private const val CHUNK_COUNT = 100
private const val LOOKUP_SIZE = 1024

fun RemoteInputStream.toLocalPathLookupFlow(): Flow<LocalPathLookup> = flow {
    if (Bugs.isTrace) log(FileOpsClient.TAG, VERBOSE) { "RemoteInputStream.toLocalPathLookupResultFlow() starting..." }

    val buffer = this@toLocalPathLookupFlow.inputStream().reader().buffered(CHUNK_COUNT * LOOKUP_SIZE)
    var sawTerminal = false
    try {
        while (currentCoroutineContext().isActive) {
            val line = buffer.readLine() ?: break

            val decodedChunk = line.decodeBase64()
                ?: throw IOException("LocalPathLookup stream: invalid base64 frame")
            val parcel = Parcel.obtain().apply {
                unmarshall(decodedChunk.toByteArray(), 0, decodedChunk.size)
                setDataPosition(0)
            }
            val wrapper = try {
                LocalPathLookupResultsIPCWrapper.createFromParcel(parcel)
            } finally {
                parcel.recycle()
            }

            if (Bugs.isTrace) {
                log(FileOpsClient.TAG, VERBOSE) { "READCHUNK: ${decodedChunk.size}B to ${wrapper.payload.size} items" }
            }
            for (result in wrapper.payload) {
                when (result) {
                    is LocalPathLookupResult.Success -> emit(result.lookup)
                    is LocalPathLookupResult.Error -> throw result.toException()
                    LocalPathLookupResult.Complete -> sawTerminal = true
                }
                // Complete is the last thing the host emits; ignore anything after it in this chunk.
                if (sawTerminal) break
            }
            // ...and stop reading further chunks once we've seen it.
            if (sawTerminal) break
        }
        // Only a real EOF (context still active) without a terminal marker means truncation.
        // If the loop exited because the consumer cancelled, let that cancellation stand.
        if (currentCoroutineContext().isActive && !sawTerminal) {
            throw IOException("LocalPathLookup stream ended without terminal event")
        }
    } finally {
        try {
            close()
        } catch (e: Exception) {
            log(FileOpsClient.TAG, ERROR) { "Failed to close RemoteInputStream: ${e.asLog()}" }
        }
    }
}

fun Flow<LocalPathLookup>.toRemoteInputStream(scope: CoroutineScope): RemoteInputStream {
    if (Bugs.isTrace) log(FileOpsHost.TAG, VERBOSE) { "Flow<LocalPathLookup>.toRemoteInputStreamWithExceptions()..." }

    val inputStream = PipedInputStream(2 * CHUNK_COUNT * LOOKUP_SIZE)
    val outputStream = PipedOutputStream()
    inputStream.connect(outputStream)

    val buffer = outputStream.writer().buffered(CHUNK_COUNT * LOOKUP_SIZE)

    val resultFlow: Flow<LocalPathLookupResult> = flow {
        try {
            this@toRemoteInputStream.collect { lookup ->
                emit(LocalPathLookupResult.Success(lookup))
            }
            // Terminal marker: tells the consumer the stream ended cleanly rather than being
            // truncated by a dying host process.
            emit(LocalPathLookupResult.Complete)
        } catch (exception: CancellationException) {
            // Cancellation is not a stream error — let it unwind instead of fabricating an Error frame.
            throw exception
        } catch (exception: Exception) {
            emit(LocalPathLookupResult.Error(exception))
        }
    }

    resultFlow
        .chunked(CHUNK_COUNT)
        .onEach { chunk: List<LocalPathLookupResult> ->
            val parcel = Parcel.obtain().apply {
                LocalPathLookupResultsIPCWrapper(chunk).writeToParcel(this, 0)
            }

            val encodedChunk = parcel.marshall().toByteString().base64()
            parcel.recycle()

            buffer.apply {
                write(encodedChunk)
                write('\n'.code)
                flush()
            }

            if (Bugs.isTrace) {
                log(FileOpsHost.TAG, VERBOSE) { "WRITECHUNK: ${chunk.size} items to ${encodedChunk.length}B" }
            }
        }
        .onCompletion {
            try {
                buffer.flush()
            } finally {
                buffer.close()
            }
        }
        .catch { exception ->
            if (exception is CancellationException) throw exception
            // Usually "Pipe closed": the client cancelled and closed its end, nobody is left to
            // report to. Must not rethrow — `scope` is the helper's unsupervised app scope, and an
            // uncaught exception there kills the whole privileged process with it (BaseRootHost).
            log(FileOpsHost.TAG, ERROR) { "Flow<LocalPathLookup>.toRemoteInputStream failed: ${exception.asLog()}" }
        }
        .launchIn(scope)

    return inputStream.remoteInputStream()
}