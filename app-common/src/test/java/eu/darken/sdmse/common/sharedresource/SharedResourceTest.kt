package eu.darken.sdmse.common.sharedresource

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import okio.IOException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.time.Duration

class SharedResourceTest : BaseTest() {
    @BeforeEach
    fun setup() {
        Bugs.apply {
            isDebug = true
            isTrace = true
        }
    }

    @AfterEach
    fun teardown() {
        Bugs.apply {
            isDebug = false
            isTrace = false
        }
    }

    @Test fun `Lease-close closes Core`() = runTest2(autoCancel = true) {
        (1..100).forEach {
            val sr = SharedResource.createKeepAlive("test", this + Dispatchers.IO, Duration.ZERO)

            sr.isClosed shouldBe true
            val lease = sr.get()
            sr.isClosed shouldBe false
            lease.close()
            sr.isClosed shouldBe true
        }
    }

    @Test fun `multiple leases`() = runTest2(autoCancel = true) {
        val sr = SharedResource.createKeepAlive("test", this + Dispatchers.IO, Duration.ZERO)

        sr.isClosed shouldBe true

        val lease1 = sr.get()
        lease1.isClosed shouldBe false
        sr.isClosed shouldBe false

        val lease2 = sr.get()
        lease2.isClosed shouldBe false

        sr.isClosed shouldBe false

        lease2.close()

        lease1.isClosed shouldBe false
        lease2.isClosed shouldBe true
        sr.isClosed shouldBe false
    }

    @Test fun `Core-close closes all leases`() = runTest2(autoCancel = true) {
        val sr = SharedResource.createKeepAlive("test", this + Dispatchers.IO, Duration.ZERO)

        sr.isClosed shouldBe true

        val lease1 = sr.get().apply {
            isClosed shouldBe false
        }

        val lease2 = sr.get().apply {
            isClosed shouldBe false
        }

        sr.close()

        sr.isClosed shouldBe true
        lease1.isClosed shouldBe true
        lease2.isClosed shouldBe true
    }

    @Test fun `started parents start children`() = runTest2(autoCancel = true) {
        val srParent = SharedResource.createKeepAlive("parent", this + Dispatchers.IO, Duration.ZERO)
        val srChild = SharedResource.createKeepAlive("child", this + Dispatchers.IO, Duration.ZERO)

        srParent.get()

        srChild.isClosed shouldBe true
        srParent.addChild(srChild)
        srChild.isClosed shouldBe false
    }

    @Test fun `closed parents dont add closed children`() = runTest2(autoCancel = true) {
        val srParent = SharedResource.createKeepAlive("parent", this + Dispatchers.IO, Duration.ZERO)
        val srChild = SharedResource.createKeepAlive("child", this + Dispatchers.IO, Duration.ZERO)

        srChild.isClosed shouldBe true
        srParent.addChild(srChild)
        srChild.isClosed shouldBe true

        srParent.get()

        srChild.isClosed shouldBe true
    }

    @Test fun `closed parents dont close added children`() = runTest2(autoCancel = true) {
        val srParent = SharedResource.createKeepAlive("parent", this + Dispatchers.IO, Duration.ZERO)
        val srChild = SharedResource.createKeepAlive("child", this + Dispatchers.IO, Duration.ZERO)

        srChild.get()
        srChild.isClosed shouldBe false
        srParent.isClosed shouldBe true

        srParent.addChild(srChild)
        srChild.isClosed shouldBe false
        srParent.isClosed shouldBe true
    }

    @Test fun `adding a closed child to a closed parent`() = runTest2(autoCancel = true) {
        val srParent = SharedResource.createKeepAlive("parent", this + Dispatchers.IO, Duration.ZERO)
        val srChild = SharedResource.createKeepAlive("child", this + Dispatchers.IO, Duration.ZERO)

        srChild.isClosed shouldBe true
        srParent.addChild(srChild)
        srChild.isClosed shouldBe true
        srParent.isClosed shouldBe true
    }

    @Test fun `childs dont keep parents alive`() = runTest2(autoCancel = true) {
        val srParent = SharedResource.createKeepAlive("parent", this + Dispatchers.IO, Duration.ZERO)
        val srChild = SharedResource.createKeepAlive("child", this + Dispatchers.IO, Duration.ZERO)

        val lease1 = srParent.get().apply {
            isClosed shouldBe false
        }

        srChild.isClosed shouldBe true
        srParent.addChild(srChild)
        srChild.isClosed shouldBe false

        lease1.close()
        lease1.isClosed shouldBe true
        srChild.isClosed shouldBe true
        srParent.isClosed shouldBe true
    }

    @Test fun `Core-close() closes children`() = runTest2(autoCancel = true) {
        val srParent = SharedResource.createKeepAlive("parent", this + Dispatchers.IO, Duration.ZERO)
        val srChild1 = SharedResource.createKeepAlive("child1", this + Dispatchers.IO, Duration.ZERO)

        (1..10).forEach {
            srParent.get()

            srParent.addChild(srChild1)
            srChild1.isClosed shouldBe false

            log { "Closing srParent" }
            srParent.close()
            srChild1.isClosed shouldBe true
        }
    }

    @Test fun `Core-close() closes all children`() = runTest2(autoCancel = true) {
        val srParent = SharedResource.createKeepAlive("parent", this + Dispatchers.IO, Duration.ZERO)
        val srChild1 = SharedResource.createKeepAlive("child1", this + Dispatchers.IO, Duration.ZERO)
        val srChild2 = SharedResource.createKeepAlive("child2", this + Dispatchers.IO, Duration.ZERO)

        (1..10).forEach {
            srParent.get()

            srParent.addChild(srChild1)
            srChild1.isClosed shouldBe false

            srParent.addChild(srChild2)
            srChild2.isClosed shouldBe false
            log { "Closing srParent" }
            srParent.close()
            srChild1.isClosed shouldBe true
            srChild2.isClosed shouldBe true
        }
    }

    @Test fun `addChild double call is noop`() = runTest2(autoCancel = true) {
        val srParent = SharedResource.createKeepAlive("parent", this + Dispatchers.IO, Duration.ZERO)
        val srChild = SharedResource.createKeepAlive("child", this + Dispatchers.IO, Duration.ZERO)

        srParent.get()

        srChild.isClosed shouldBe true
        srParent.addChild(srChild)
        srParent.addChild(srChild)
        srChild.isClosed shouldBe false
    }

    @Test fun `error during creation is forwarded`() = runTest2(autoCancel = true) {
        val srError = SharedResource<Unit>(
            tag = "parent",
            parentScope = this@runTest2 + Dispatchers.IO,
            stopTimeout = Duration.ZERO,
            source = flow { throw IOException() }
        )

        shouldThrow<IOException> { srError.get() }
        shouldThrow<IOException> { srError.get() }
        shouldThrow<IOException> { srError.get() }
    }

    @Test fun `cancel during source creation`(): Unit = runBlocking {
        val srCancel = SharedResource<Unit>(
            tag = "parent",
            parentScope = this + Dispatchers.IO,
            stopTimeout = Duration.ZERO,
            source = flow {
                emit(Unit)
                awaitCancellation()
            }
        )
        (1..100).forEach {
            val job1 = launch(Dispatchers.IO) {
                srCancel.get().apply {
                    isClosed shouldBe false
                    close()
                }
            }
            val job2 = launch(Dispatchers.IO) {
                delay(1)
                srCancel.get().apply {
                    isClosed shouldBe false
                    close()
                }
            }
            job1.cancelAndJoin()
            job2.join()
        }
    }

    @Test fun `racing to get() - zerg rush`(): Unit = runBlocking {
        val srRace = SharedResource<Unit>(
            tag = "parent",
            parentScope = this + Dispatchers.Default,
            stopTimeout = Duration.ZERO,
            source = flow {
                emit(Unit)
                awaitCancellation()
            }
        )
        val dispatcher = Dispatchers.IO.limitedParallelism(128)
        val jobs = (1..100).map {
            launch(dispatcher) {
                srRace.get().apply {
                    isClosed shouldBe false
                    delay(1)
                    close()
                }
            }
        }
        log { "Waiting for jobs to join" }
        jobs.joinAll()
    }

    @Test fun `racing to get() - team up`(): Unit = runBlocking {
        val sr = SharedResource<Unit>(
            tag = "sr",
            parentScope = this@runBlocking + Dispatchers.Default,
            stopTimeout = Duration.ZERO,
            source = flow {
                delay(1)
                emit(Unit)
                awaitCancellation()
            }
        )

        (1..100).map { i ->
            val leadLease = sr.get()
            val jobs = mutableSetOf<Job>()

            launch(Dispatchers.IO) {
                shouldNotThrowAny {
                    sr.get().close()
                }
            }.also { jobs.add(it) }

            launch(Dispatchers.IO) {
                shouldNotThrowAny {
                    leadLease.close()
                }
            }.also { jobs.add(it) }

            jobs.joinAll()
        }

        sr.isClosed shouldBe true
    }

    @Test fun `parent closes with already closed child`() = runTest2(autoCancel = true) {
        val srParent = SharedResource.createKeepAlive("parent", this + Dispatchers.IO, Duration.ZERO)
        val srChild = SharedResource.createKeepAlive("child", this + Dispatchers.IO, Duration.ZERO)

        srParent.get()
        srParent.isClosed shouldBe false
        srParent.addChild(srChild)
        srChild.isClosed shouldBe false

        srChild.close()
        srChild.isClosed shouldBe true
        srParent.isClosed shouldBe false

        srParent.close()
        srParent.isClosed shouldBe true
    }

    @Test fun `closing on the same scope deadlock check`() = runTest2(autoCancel = true) {
        val sr = SharedResource.createKeepAlive("test", this + Dispatchers.IO, Duration.ZERO)

        sr.isClosed shouldBe true
        val lease = sr.get()
        sr.isClosed shouldBe false
        lease.close()
        sr.isClosed shouldBe true
        sr.get()
        sr.isClosed shouldBe false
    }

    @Test fun `lease check is NOT pre-empted by global close`() = runBlocking {
        var counter = 0
        val sr = SharedResource(
            tag = "parent",
            parentScope = this + Dispatchers.Default,
            stopTimeout = Duration.ofMillis(1000),
            source = flow {
                counter++
                emit(counter)
                awaitCancellation()
            }
        )

        val lease1 = sr.get().apply {
            item shouldBe 1
        }
        sr.close()
        lease1.isClosed shouldBe true
        sr.isClosed shouldBe false

        delay(500)

        sr.isClosed shouldBe false
        val lease2 = sr.get().apply {
            item shouldBe 1
        }
        sr.close()
        lease2.isClosed shouldBe true
        sr.isClosed shouldBe false

        delay(1500)

        sr.isClosed shouldBe true
        val lease3 = sr.get().apply {
            item shouldBe 2
        }
        sr.close()
        lease3.isClosed shouldBe true
        sr.isClosed shouldBe false

        delay(1500)

        sr.isClosed shouldBe true
    }

    @Test fun `lease check is cancelled by adding new leases`() = runBlocking {
        var counter = 0
        val sr = SharedResource(
            tag = "parent",
            parentScope = this + Dispatchers.Default,
            stopTimeout = Duration.ofMillis(1000),
            source = flow {
                counter++
                emit(counter)
                awaitCancellation()
            }
        )

        val lease1 = sr.get().apply {
            item shouldBe 1
            close()
        }
        lease1.isClosed shouldBe true
        sr.isClosed shouldBe false

        delay(500)

        sr.isClosed shouldBe false
        val lease2 = sr.get().apply {
            item shouldBe 1
            close()
        }
        lease2.isClosed shouldBe true
        sr.isClosed shouldBe false

        delay(1500)

        sr.isClosed shouldBe true
        val lease3 = sr.get().apply {
            item shouldBe 2
            close()
        }
        lease3.isClosed shouldBe true
        sr.isClosed shouldBe false

        delay(1500)

        sr.isClosed shouldBe true
    }

    @Test fun `verboseLifecycle promotes lifecycle logs to non-trace`() = runTest2(autoCancel = true) {
        // This test specifically asserts non-trace behavior
        Bugs.isTrace = false

        val captured = mutableListOf<String>()
        val capture = object : Logging.Logger {
            override fun log(
                priority: Logging.Priority,
                tag: String,
                message: String,
                metaData: Map<String, Any>?
            ) {
                if (tag.endsWith(":SR")) captured.add(message)
            }
        }
        Logging.install(capture)
        try {
            val srVerbose = SharedResource(
                tag = "verbose",
                parentScope = this + Dispatchers.IO,
                stopTimeout = Duration.ZERO,
                source = flow {
                    // Force a real wait so the cold-start "Waiting for source value..." path fires
                    delay(50)
                    emit(Unit)
                    awaitCancellation()
                },
                verboseLifecycle = true,
            )

            val lease = srVerbose.get()
            lease.close()

            // Lifecycle breadcrumbs should fire even though Bugs.isTrace == false
            captured.any { it.contains("Launching source job") } shouldBe true
            captured.any { it.contains("Starting source") } shouldBe true
            captured.any { it.contains("Waiting for source value") } shouldBe true
            captured.any { it.contains("onCompletion due to") } shouldBe true

            // Sanity: a non-verbose SharedResource in the same conditions stays silent
            captured.clear()
            val srSilent = SharedResource(
                tag = "silent",
                parentScope = this + Dispatchers.IO,
                stopTimeout = Duration.ZERO,
                source = flow {
                    emit(Unit)
                    awaitCancellation()
                },
                // verboseLifecycle defaults to false
            )
            srSilent.get().close()
            captured.any { it.contains("Launching source job") } shouldBe false
        } finally {
            Logging.remove(capture)
        }
    }

    @Test fun `warm cache-hit get() does not log Source-job-already-exists or Waiting`() = runTest2(autoCancel = true) {
        Bugs.isTrace = false

        val captured = mutableListOf<String>()
        val capture = object : Logging.Logger {
            override fun log(
                priority: Logging.Priority,
                tag: String,
                message: String,
                metaData: Map<String, Any>?
            ) {
                if (tag.endsWith(":SR")) captured.add(message)
            }
        }
        Logging.install(capture)
        try {
            val sr = SharedResource(
                tag = "warm",
                parentScope = this + Dispatchers.IO,
                stopTimeout = Duration.ofSeconds(5),
                source = flow {
                    emit(Unit)
                    awaitCancellation()
                },
                verboseLifecycle = true,
            )

            // First acquisition — cold start, populates the cache
            val firstLease = sr.get()
            // Clear logs from the cold-start path
            captured.clear()

            // Second acquisition — cache hit, must not log either lifecycle breadcrumb
            val secondLease = sr.get()
            captured.any { it.contains("Source job already exists") } shouldBe false
            captured.any { it.contains("Waiting for source value") } shouldBe false

            secondLease.close()
            firstLease.close()
        } finally {
            Logging.remove(capture)
        }
    }

    @Test fun `addChild refreshes a stale closed child`() = runTest2(autoCancel = true) {
        val parent = SharedResource.createKeepAlive("parent", this + Dispatchers.IO, Duration.ZERO)
        // Keep parent alive across the whole test
        val parentLease = parent.get()

        val child = SharedResource.createKeepAlive("child", this + Dispatchers.IO, Duration.ZERO)

        // Adopt: parent acquires a keep-alive lease on child
        parent.addChild(child)
        child.isClosed shouldBe false

        // Force child to complete: close all direct leases on it. Parent holds the only lease via addChild.
        child.close()
        child.isClosed shouldBe true

        // Re-adopt after the child source completed
        parent.addChild(child)

        // The child must be alive again — proves the stale entry was refreshed, not silently kept
        child.isClosed shouldBe false

        parentLease.close()
    }

    @Test fun `racing global close`(): Unit = runBlocking {
        val sr = SharedResource<Unit>(
            tag = "sr",
            parentScope = this@runBlocking + Dispatchers.Default,
            stopTimeout = Duration.ZERO,
            source = flow {
                delay(1)
                emit(Unit)
                awaitCancellation()
            }
        )

        (1..100).map { i ->
            val jobs = mutableSetOf<Job>()

            sr.get()

            launch(Dispatchers.IO) {
                try {
                    sr.get().close()
                } catch (e: Exception) {
                    log { "Thrown ${e.asLog()}" }
                    (e is CancellationException) shouldBe true
                }
            }.also { jobs.add(it) }

            launch(Dispatchers.IO) {
                shouldNotThrowAny {
                    sr.close()
                }
            }.also { jobs.add(it) }

            jobs.joinAll()

            sr.close()
            sr.isClosed shouldBe true
        }
    }
}