package eu.darken.sdmse.common.backup

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BackupOperationGateTest : BaseTest() {

    @Test
    fun `results propagate through both entry points`() = runTest {
        val gate = BackupOperationGate()
        gate.runExclusive { 42 } shouldBe 42
        gate.runShared { "ok" } shouldBe "ok"
    }

    @Test
    fun `exclusive refuses while shared work is active`() = runTest {
        val gate = BackupOperationGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val worker = launch { gate.runShared { entered.complete(Unit); release.await() } }
        entered.await()

        shouldThrow<BackupBusyException> { gate.runExclusive { } }

        release.complete(Unit)
        worker.join()
        // Usable again once the shared work released.
        gate.runExclusive { "later" } shouldBe "later"
    }

    @Test
    fun `exclusive refuses while another exclusive is active`() = runTest {
        val gate = BackupOperationGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val backup = launch { gate.runExclusive { entered.complete(Unit); release.await() } }
        entered.await()

        shouldThrow<BackupBusyException> { gate.runExclusive { } }

        release.complete(Unit)
        backup.join()
    }

    @Test
    fun `shared work waits for an active exclusive to finish`() = runTest {
        val gate = BackupOperationGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val backup = launch { gate.runExclusive { entered.complete(Unit); release.await() } }
        entered.await()

        var sharedRan = false
        val shared = launch { gate.runShared { sharedRan = true } }
        advanceUntilIdle()
        sharedRan shouldBe false

        release.complete(Unit)
        backup.join()
        shared.join()
        sharedRan shouldBe true
    }

    @Test
    fun `multiple shared operations run concurrently`() = runTest {
        val gate = BackupOperationGate()
        val entered1 = CompletableDeferred<Unit>()
        val entered2 = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val w1 = launch { gate.runShared { entered1.complete(Unit); release.await() } }
        val w2 = launch { gate.runShared { entered2.complete(Unit); release.await() } }

        // Both are inside the gate at the same time.
        entered1.await()
        entered2.await()

        release.complete(Unit)
        w1.join()
        w2.join()
    }

    @Test
    fun `state resets after failures`() = runTest {
        val gate = BackupOperationGate()

        shouldThrow<RuntimeException> { gate.runExclusive { throw RuntimeException("boom") } }
        shouldThrow<RuntimeException> { gate.runShared { throw RuntimeException("boom") } }

        gate.runExclusive { "ok" } shouldBe "ok"
        gate.runShared { "ok" } shouldBe "ok"
    }
}
