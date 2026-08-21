package eu.darken.sdmse.appcontrol.core.export

import eu.darken.sdmse.common.compression.Zipper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * The copy loops are blocking, so cancellation only takes effect if it is checked between chunks.
 * Both tests cancel from inside the first read, i.e. while the copy is under way.
 */
class ChunkedCopyTest : BaseTest() {

    private val chunk = Zipper.BUFFER

    @Test
    fun `the okio copy stops after the chunk that was in flight`() {
        val job = Job()
        var reads = 0
        val upstream = Buffer().write(ByteArray(chunk * 3))
        val source = object : ForwardingSource(upstream) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                reads++
                job.cancel()
                return super.read(sink, byteCount)
            }
        }
        val sink = Buffer()

        shouldThrow<CancellationException> { copyChunked(source.buffer(), sink, job) }

        reads shouldBe 1
        sink.size shouldBe chunk.toLong()
    }

    @Test
    fun `the stream copy stops after the chunk that was in flight`() {
        val job = Job()
        var reads = 0
        val input = object : InputStream() {
            override fun read(): Int = throw UnsupportedOperationException("Chunk reads only")

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                reads++
                job.cancel()
                b.fill(1, off, off + len)
                return len
            }
        }
        val output = ByteArrayOutputStream()

        shouldThrow<CancellationException> { copyChunked(input, output, job) }

        reads shouldBe 1
        output.size() shouldBe chunk
    }

    @Test
    fun `an uncancelled copy passes everything through`() {
        val job = Job()
        val sink = Buffer()

        val copied = copyChunked(Buffer().write(ByteArray(chunk * 3 + 1)), sink, job)

        copied shouldBe (chunk * 3 + 1).toLong()
        sink.size shouldBe (chunk * 3 + 1).toLong()
    }
}
