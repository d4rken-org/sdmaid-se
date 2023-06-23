package eu.darken.sdmse.common.ipc


import android.os.RemoteException
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import okio.Source
import okio.source
import java.io.IOException
import java.io.InputStream


/**
 * InputStream is a pretty basic class that is easily wrapped, because it really only requires
 * a handful of base methods.
 *
 *
 * We cannot make an InputStream into a Binder-passable interface directly, because its definitions
 * includes throwing IOExceptions. It also defines multiple methods with the same name and a
 * different parameter list, which is not supported in aidl.
 *
 *
 * You should never throw an exception in your Binder interface. We catch the exceptions and
 * return -2 instead, because conveniently all the methods we override should return values >= -1.
 * More complex classes would require more complex solutions.
 */

/**
 * Use this on the root side
 */
internal fun InputStream.remoteInputStream(): RemoteInputStream.Stub = object : RemoteInputStream.Stub() {

    override fun available(): Int = try {
        this@remoteInputStream.available()
    } catch (e: IOException) {
        log(ERROR) { "available() failed: ${e.asLog()}" }
        -2
    }

    override fun read(): Int = try {
        this@remoteInputStream.read()
    } catch (e: IOException) {
        log(ERROR) { "read() failed: ${e.asLog()}" }
        -2
    }

    override fun readBuffer(b: ByteArray, off: Int, len: Int): Int = try {
        this@remoteInputStream.read(b, off, len)
    } catch (e: IOException) {
        log(ERROR) { "readBuffer() failed: ${e.asLog()}" }
        -2
    }

    override fun close() = try {
        this@remoteInputStream.close()
    } catch (e: IOException) {
    }

}

/**
 * We throw an IOException if we receive a -2 result, because we know we caught one on the
 * other end in that case. The logcat output will obviously not show the real reason for the
 * exception.
 *
 *
 * We also callbacks the InputStream we create inside a BufferedInputStream, to potentially reduce
 * the number of calls made. We increase the buffer size to 64kb in case this is ever used
 * to actually read large files, which is quite a bit faster than the default 8kb.
 *
 *
 * Use this on the non-root side.
 */
internal fun RemoteInputStream.inputStream(): InputStream = object : InputStream() {

    @Throws(IOException::class)
    private fun throwIO(r: Int): Int {
        if (r == -2) throw IOException("Remote Exception")
        return r
    }

    @Throws(IOException::class)
    override fun available(): Int = try {
        throwIO(this@inputStream.available())
    } catch (e: RemoteException) {
        throw IOException("Remote Exception", e)
    }

    @Throws(IOException::class)
    override fun read(): Int = try {
        throwIO(this@inputStream.read())
    } catch (e: RemoteException) {
        throw IOException("Remote Exception", e)
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray): Int = read(b, 0, b.size)

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int = try {
        throwIO(this@inputStream.readBuffer(b, off, len))
    } catch (e: RemoteException) {
        throw IOException("Remote Exception", e)
    }

    @Throws(IOException::class)
    override fun close() = try {
        this@inputStream.close()
    } catch (e: RemoteException) {
        throw IOException("Remote Exception", e)
    }
}

fun RemoteInputStream.source(): Source = inputStream().source()