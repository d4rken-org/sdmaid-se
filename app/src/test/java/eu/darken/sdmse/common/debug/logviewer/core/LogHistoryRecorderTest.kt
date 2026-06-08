package eu.darken.sdmse.common.debug.logviewer.core

import eu.darken.sdmse.common.debug.logging.Logging
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class LogHistoryRecorderTest : BaseTest() {

    private fun create() = LogHistoryRecorder()

    private fun LogHistoryRecorder.emit(message: String, priority: Logging.Priority = Logging.Priority.DEBUG) =
        log(priority, "TestTag", message, null)

    @Test
    fun `ring buffer caps at BUFFER_CAP and drops oldest`() {
        val recorder = create()
        val total = LogHistoryRecorder.BUFFER_CAP + 500
        repeat(total) { recorder.emit("line $it") }

        val snapshot = recorder.snapshot()
        snapshot shouldHaveSize LogHistoryRecorder.BUFFER_CAP
        snapshot.first().id shouldBe (total - LogHistoryRecorder.BUFFER_CAP).toLong()
        snapshot.last().id shouldBe (total - 1).toLong()
    }

    @Test
    fun `multiline messages are split into separate lines`() {
        val recorder = create()
        recorder.emit("line1\nline2\nline3")

        val snapshot = recorder.snapshot()
        snapshot shouldHaveSize 3
        snapshot.map { it.message } shouldBe listOf("line1", "line2", "line3")
    }

    @Test
    fun `verbose is filtered by default, others are loggable`() {
        val recorder = create()
        recorder.isLoggable(Logging.Priority.VERBOSE) shouldBe false
        recorder.isLoggable(Logging.Priority.DEBUG) shouldBe true
        recorder.isLoggable(Logging.Priority.ERROR) shouldBe true
    }

    @Test
    fun `minPriority raises the capture threshold`() {
        val recorder = create()
        recorder.minPriority = Logging.Priority.WARN
        recorder.isLoggable(Logging.Priority.VERBOSE) shouldBe false
        recorder.isLoggable(Logging.Priority.INFO) shouldBe false
        recorder.isLoggable(Logging.Priority.WARN) shouldBe true
        recorder.isLoggable(Logging.Priority.ERROR) shouldBe true

        recorder.minPriority = Logging.Priority.VERBOSE
        recorder.isLoggable(Logging.Priority.VERBOSE) shouldBe true
    }

    @Test
    fun `pause freezes the buffer and counts skipped lines`() {
        val recorder = create()
        recorder.emit("before")

        recorder.setPaused(true)
        recorder.emit("during1")
        recorder.emit("during2\nduring2b")

        val frozen = recorder.snapshot()
        frozen shouldHaveSize 1
        frozen.single().message shouldBe "before"
        recorder.read().isPaused shouldBe true
        // "during1" (1) + "during2\nduring2b" (2) = 3 skipped lines
        recorder.read().droppedWhilePaused shouldBe 3

        recorder.setPaused(false)
        recorder.read().droppedWhilePaused shouldBe 0
        recorder.emit("after")
        recorder.snapshot().map { it.message } shouldBe listOf("before", "after")
    }

    @Test
    fun `clear empties the buffer but keeps ids monotonic`() {
        val recorder = create()
        recorder.emit("a")
        recorder.emit("b")
        recorder.snapshot() shouldHaveSize 2

        recorder.clear()
        recorder.snapshot() shouldHaveSize 0

        recorder.emit("c")
        val snapshot = recorder.snapshot()
        snapshot shouldHaveSize 1
        snapshot.single().message shouldBe "c"
        // ids keep advancing across the clear: a=0, b=1, c=2
        snapshot.single().id shouldBe 2L
    }

    @Test
    fun `concurrent logging keeps ids unique and monotonic`() {
        val recorder = create()
        val threads = 8
        val perThread = 1000
        val latch = CountDownLatch(1)

        val workers = (0 until threads).map {
            thread(start = false) {
                latch.await()
                repeat(perThread) { recorder.emit("msg") }
            }
        }
        workers.forEach { it.start() }
        latch.countDown()
        workers.forEach { it.join() }

        val ids = recorder.snapshot().map { it.id }
        ids shouldHaveSize LogHistoryRecorder.BUFFER_CAP
        // Ids are assigned under the lock, so insertion order is strictly increasing & unique.
        ids shouldBe ids.sorted()
        ids.toSet() shouldHaveSize ids.size
        ids.last() shouldBe (threads.toLong() * perThread - 1)
    }

    @Test
    fun `acquire installs and release removes, ref-counted`() {
        val recorder = create()
        try {
            recorder.acquire()
            Logging.loggers.contains(recorder) shouldBe true

            // Second owner keeps it installed after a single release.
            recorder.acquire()
            recorder.release()
            Logging.loggers.contains(recorder) shouldBe true
        } finally {
            recorder.release()
        }
        Logging.loggers.contains(recorder) shouldBe false

        // An extra release is a no-op and never drives the count negative.
        recorder.release()
        Logging.loggers.contains(recorder) shouldBe false
    }
}
