package eu.darken.sdmse.common.ipc

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.PipedInputStream
import java.lang.ref.Reference
import java.lang.ref.WeakReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class RemoteInputStreamExtensionsTest : BaseTest() {

    @Test
    fun `reads delegate to the wrapped stream until close`() {
        val stub = ByteArrayInputStream(byteArrayOf(1, 2, 3)).remoteInputStream()
        val buffer = ByteArray(4)

        stub.read() shouldBe 1
        stub.readBuffer(buffer, 0, 4) shouldBe 2
        buffer[0] shouldBe 2.toByte()
        buffer[1] shouldBe 3.toByte()
        stub.available() shouldBe 0

        stub.close()

        stub.read() shouldBe -2
        stub.readBuffer(buffer, 0, 4) shouldBe -2
        stub.available() shouldBe -2
        stub.close()
    }

    // Built in its own frame so no local of the test method keeps the stream reachable.
    private fun wrap(): Pair<RemoteInputStream.Stub, WeakReference<InputStream>> {
        val stream = PipedInputStream(32 * 1024)
        return stream.remoteInputStream() to WeakReference<InputStream>(stream)
    }

    @Test
    fun `close releases the wrapped stream`() {
        val (stub, weak) = wrap()

        var attempts = 0
        while (attempts < 50 && weak.get() != null) {
            System.gc()
            Thread.sleep(100)
            attempts++
        }
        weak.get() shouldNotBe null

        stub.close()

        attempts = 0
        while (attempts < 50 && weak.get() != null) {
            System.gc()
            Thread.sleep(100)
            attempts++
        }
        weak.get() shouldBe null

        Reference.reachabilityFence(stub)
    }
}
