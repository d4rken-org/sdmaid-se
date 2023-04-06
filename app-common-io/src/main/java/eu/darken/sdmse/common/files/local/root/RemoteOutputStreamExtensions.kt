package eu.darken.sdmse.common.files.local.root


import android.os.RemoteException
import okio.Sink
import okio.sink
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream


/**
 * Use this on the root side
 */
internal fun OutputStream.toRemoteOutputStream(): RemoteOutputStream.Stub = object : RemoteOutputStream.Stub() {
    override fun write(b: Int) = try {
        this@toRemoteOutputStream.write(b)
    } catch (e: IOException) {
        Timber.e(e)
    }

    override fun writeBuffer(b: ByteArray, off: Int, len: Int) = try {
        this@toRemoteOutputStream.write(b, off, len)
    } catch (e: IOException) {
        Timber.e(e)
    }

    override fun flush() = try {
        this@toRemoteOutputStream.flush()
    } catch (e: IOException) {
        Timber.e(e)
    }

    override fun close() = try {
        this@toRemoteOutputStream.close()
    } catch (e: IOException) {
        // no action required
    }

}

/**
 * Use this on the non-root side.
 */
internal fun RemoteOutputStream.outputStream(): OutputStream = object : OutputStream() {

    @Throws(IOException::class)
    override fun write(b: Int) = try {
        this@outputStream.write(b)
    } catch (e: RemoteException) {
        throw IOException("Remote Exception during write($b)", e)
    }

    override fun write(b: ByteArray) = writeBuffer(b, 0, b.size)

    override fun write(b: ByteArray, off: Int, len: Int) = try {
        this@outputStream.writeBuffer(b, 0, len)
    } catch (e: RemoteException) {
        throw IOException("Remote Exception during write(size=${b.size}, off=$off, len=$len)", e)
    }

    override fun close() = try {
        this@outputStream.close()
    } catch (e: RemoteException) {
        throw IOException("Remote Exception during close() ", e)
    }

    override fun flush() = try {
        this@outputStream.flush()
    } catch (e: RemoteException) {
        throw IOException("Remote Exception during flush()", e)
    }
}

fun RemoteOutputStream.sink(): Sink = outputStream().sink()