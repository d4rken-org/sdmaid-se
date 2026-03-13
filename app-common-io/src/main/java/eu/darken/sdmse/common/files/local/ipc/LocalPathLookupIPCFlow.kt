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
import java.io.PipedInputStream
import java.io.PipedOutputStream


private const val CHUNK_COUNT = 100
private const val LOOKUP_SIZE = 1024

fun RemoteInputStream.toLocalPathLookupFlow(): Flow<LocalPathLookup> = flow {
    if (Bugs.isTrace) log(FileOpsClient.TAG, VERBOSE) { "RemoteInputStream.toLocalPathLookupResultFlow() starting..." }

    val buffer = this@toLocalPathLookupFlow.inputStream().reader().buffered(CHUNK_COUNT * LOOKUP_SIZE)
    while (currentCoroutineContext().isActive) {
        val line = buffer.readLine() ?: break

        val decodedChunk = line.decodeBase64()!!
        val parcel = Parcel.obtain().apply {
            unmarshall(decodedChunk.toByteArray(), 0, decodedChunk.size)
            setDataPosition(0)
        }

        val wrapper = LocalPathLookupResultsIPCWrapper.createFromParcel(parcel)

        if (Bugs.isTrace) {
            log(FileOpsClient.TAG, VERBOSE) { "READCHUNK: ${decodedChunk.size}B to ${wrapper.payload.size} items" }
        }
        wrapper.payload.forEach { result ->
            when (result) {
                is LocalPathLookupResult.Success -> emit(result.lookup)
                is LocalPathLookupResult.Error -> throw result.toException()
            }
        }

        parcel.recycle()
    }

    close()
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
            buffer.flush()
            buffer.close()
        }
        .catch {
            log(FileOpsHost.TAG, ERROR) { "toRemoteInputStreamWithExceptions failed: ${it.asLog()}" }
            throw it
        }
        .launchIn(scope)

    return inputStream.remoteInputStream()
}