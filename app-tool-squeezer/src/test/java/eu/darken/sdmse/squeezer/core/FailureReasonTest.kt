package eu.darken.sdmse.squeezer.core

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.IOException

class FailureReasonTest : BaseTest() {

    @Test
    fun `InsufficientStorageException maps to INSUFFICIENT_STORAGE`() {
        val e = InsufficientStorageException(requiredBytes = 1_000L, availableBytes = 100L)
        e.toFailureReason() shouldBe FailureReason.INSUFFICIENT_STORAGE
    }

    @Test
    fun `UnsupportedFormatException maps to CODEC_UNSUPPORTED`() {
        val e = UnsupportedFormatException("codec missing")
        e.toFailureReason() shouldBe FailureReason.CODEC_UNSUPPORTED
    }

    @Test
    fun `CancellationException maps to CANCELLED`() {
        val e = CancellationException("interrupted")
        e.toFailureReason() shouldBe FailureReason.CANCELLED
    }

    @Test
    fun `IOException (non-typed) maps to IO_ERROR`() {
        val e = IOException("disk read failed")
        e.toFailureReason() shouldBe FailureReason.IO_ERROR
    }

    @Test
    fun `generic Throwable maps to UNKNOWN`() {
        val e = RuntimeException("surprise")
        e.toFailureReason() shouldBe FailureReason.UNKNOWN
    }

    @Test
    fun `aggregation via groupingBy - counts per reason`() {
        val failures: List<Throwable> = listOf(
            InsufficientStorageException(100L, 10L),
            InsufficientStorageException(200L, 20L),
            UnsupportedFormatException("bad codec"),
            IOException("generic io"),
            IOException("another io"),
            IOException("more io"),
            RuntimeException("weird"),
        )

        val counts: Map<FailureReason, Int> = failures
            .map { it.toFailureReason() }
            .groupingBy { it }
            .eachCount()

        counts[FailureReason.INSUFFICIENT_STORAGE] shouldBe 2
        counts[FailureReason.CODEC_UNSUPPORTED] shouldBe 1
        counts[FailureReason.IO_ERROR] shouldBe 3
        counts[FailureReason.UNKNOWN] shouldBe 1
        counts[FailureReason.CANCELLED] shouldBe null
    }
}
