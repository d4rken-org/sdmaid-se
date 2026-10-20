package eu.darken.sdmse.common.areas.modules

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFGateway
import eu.darken.sdmse.common.files.saf.SAFPath
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class AreaAccessProbeTest : BaseTest() {

    private val probe = LocalPath.build("/storage/emulated/0/probe")

    private class FakeProbe(
        var present: Boolean = false,
        val onCreate: suspend FakeProbe.() -> Unit = { this.present = true },
        val onExists: suspend FakeProbe.(call: Int) -> Boolean = { this.present },
        val onDelete: suspend FakeProbe.() -> Unit = { this.present = false },
    ) {
        var creates = 0
        var existsCalls = 0
        var deletes = 0

        suspend fun create() {
            creates++
            onCreate()
        }

        suspend fun exists(): Boolean = onExists(++existsCalls)

        suspend fun delete() {
            deletes++
            onDelete()
        }
    }

    private suspend fun FakeProbe.probeIt() = probeWriteAccess(
        tag = "Test",
        probe = probe,
        create = { create() },
        exists = { exists() },
        delete = { delete() },
        cleanupTimeout = 5.seconds,
    )

    @Test
    fun `accessible probe is created and removed`() = runTest {
        val fake = FakeProbe()

        fake.probeIt() shouldBe true

        fake.creates shouldBe 1
        fake.deletes shouldBe 1
        fake.present shouldBe false
    }

    @Test
    fun `existing file at the probe path is left alone`() = runTest {
        val fake = FakeProbe(present = true)

        fake.probeIt() shouldBe false

        fake.creates shouldBe 0
        fake.deletes shouldBe 0
        fake.present shouldBe true
    }

    @Test
    fun `failed create still attempts cleanup`() = runTest {
        // The create can have taken effect even though it reported an error
        val fake = FakeProbe(onCreate = {
            present = true
            throw IOException("create failed")
        })

        fake.probeIt() shouldBe false

        fake.deletes shouldBe 1
        fake.present shouldBe false
    }

    @Test
    fun `cancellation after create returned still removes the probe`() = runTest {
        val created = CompletableDeferred<Unit>()
        val fake = FakeProbe(onExists = { call ->
            if (call == 1) return@FakeProbe present
            created.complete(Unit)
            awaitCancellation()
        })

        val job = launch { fake.probeIt() }
        created.await()
        job.cancel()
        job.join()

        job.isCancelled shouldBe true
        fake.deletes shouldBe 1
        fake.present shouldBe false
    }

    @Test
    fun `cancellation during create still removes the probe`() = runTest {
        val created = CompletableDeferred<Unit>()
        val fake = FakeProbe(
            onCreate = {
                present = true
                created.complete(Unit)
                awaitCancellation()
            },
            onDelete = {
                // Cleanup has to get past its own suspension points while the caller is cancelled
                delay(100)
                present = false
            },
        )

        val job = launch { fake.probeIt() }
        created.await()
        job.cancel()
        job.join()

        fake.deletes shouldBe 1
        fake.present shouldBe false
    }

    @Test
    fun `failing cleanup does not change the result`() = runTest {
        val fake = FakeProbe(onDelete = { throw IOException("delete failed") })

        fake.probeIt() shouldBe true
        fake.deletes shouldBe 1
    }

    @Test
    fun `hanging cleanup is bounded by the timeout`() = runTest {
        val fake = FakeProbe(onDelete = { awaitCancellation() })

        fake.probeIt() shouldBe true
        fake.deletes shouldBe 1
        currentTime shouldBe 5_000L
    }

    @Test
    fun `cancelled caller does not get a result for an existing probe`() = runTest {
        lateinit var job: Job
        var returned = false
        val fake = FakeProbe(present = true, onExists = {
            job.cancel()
            present
        })

        job = launch {
            fake.probeIt()
            returned = true
        }
        job.join()

        returned shouldBe false
        fake.deletes shouldBe 0
    }

    @Test
    fun `unexpected errors propagate after cleanup`() = runTest {
        val fake = FakeProbe(onExists = { call ->
            if (call == 1) present else throw IllegalStateException("service lost")
        })

        shouldThrow<IllegalStateException> { fake.probeIt() }

        fake.deletes shouldBe 1
        fake.present shouldBe false
    }

    @Test
    fun `cancelled caller does not get a result`() = runTest {
        lateinit var job: Job
        var returned = false
        val fake = FakeProbe(onExists = { call ->
            if (call == 1) return@FakeProbe present
            // A lower layer turning the caller's cancellation into an IOException
            job.cancel()
            throw IOException("interrupted")
        })

        job = launch {
            fake.probeIt()
            returned = true
        }
        job.join()

        returned shouldBe false
        fake.deletes shouldBe 1
    }

    @Test
    fun `LocalGateway probe cleanup ignores dry-run and keeps the mode`() = runTest {
        val gateway = mockk<LocalGateway>().apply {
            coEvery { exists(probe, mode = LocalGateway.Mode.ROOT) } returnsMany listOf(false, true)
            coEvery { createFile(probe, mode = LocalGateway.Mode.ROOT) } just Runs
            coEvery { delete(probe, recursive = any(), mode = any(), dryRun = any()) } just Runs
        }

        Bugs.isDryRun = true
        try {
            gateway.probeWriteAccess("Test", probe, LocalGateway.Mode.ROOT) shouldBe true
        } finally {
            Bugs.isDryRun = false
        }

        coVerify(exactly = 1) {
            gateway.delete(probe, recursive = false, mode = LocalGateway.Mode.ROOT, dryRun = false)
        }
    }

    @Test
    fun `SAFGateway probe cleanup ignores dry-run`() = runTest {
        val safProbe = mockk<SAFPath>(relaxed = true)
        val gateway = mockk<SAFGateway>().apply {
            coEvery { exists(safProbe) } returnsMany listOf(false, true)
            coEvery { createFile(safProbe) } just Runs
            coEvery { delete(safProbe, recursive = any(), dryRun = any()) } just Runs
        }

        Bugs.isDryRun = true
        try {
            gateway.probeWriteAccess("Test", safProbe) shouldBe true
        } finally {
            Bugs.isDryRun = false
        }

        coVerify(exactly = 1) { gateway.delete(safProbe, recursive = false, dryRun = false) }
    }
}
