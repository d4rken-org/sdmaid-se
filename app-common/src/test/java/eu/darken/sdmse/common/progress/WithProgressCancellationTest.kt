package eu.darken.sdmse.common.progress

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class WithProgressCancellationTest : BaseTest() {

    private class FakeHost(override val progress: Flow<Progress.Data?>) : Progress.Host

    private class RecordingClient : Progress.Client {
        @Volatile
        var current: Progress.Data? = null
            private set

        override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
            synchronized(this) { current = update(current) }
        }
    }

    @Test
    fun `cancelling the caller still restores the client progress`() = runTest2 {
        withContext(Dispatchers.Default) {
            val hostProgress = Progress.Data(extra = "host-progress")
            val restored = Progress.Data(extra = "restored")

            val client = RecordingClient()
            val host = FakeHost(MutableStateFlow(hostProgress))
            val actionStarted = CompletableDeferred<Unit>()

            val job = launch {
                host.withProgress(client, onCompletion = { restored }) {
                    actionStarted.complete(Unit)
                    awaitCancellation()
                }
            }
            actionStarted.await()

            job.cancelAndJoin()

            withClue(
                "withProgress() did not restore the client after the caller was cancelled, it is left holding " +
                    "the host's last forwarded progress. The finally block is cancellable, so " +
                    "forwardingJob.cancelAndJoin() throws CancellationException on an already-cancelled caller " +
                    "and neither scope.cancel() nor the restoring updateProgress() ever run."
            ) {
                client.current?.extra shouldBe restored.extra
            }
        }
    }
}
