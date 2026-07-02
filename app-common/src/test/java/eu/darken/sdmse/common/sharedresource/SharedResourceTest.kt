package eu.darken.sdmse.common.sharedresource

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.IOException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

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

            // The source teardown (and its onCompletion breadcrumb) now runs asynchronously off-lock,
            // so wait for it before asserting. Runs on real Dispatchers.IO, hence the real-time wait.
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(2_000) {
                    while (captured.none { it.contains("onCompletion due to") }) delay(10)
                }
            }

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

    @Test fun `get() does not block on a slow-closing generation`(): Unit = runBlocking {
        val releaseTeardown = CompletableDeferred<Unit>()
        var sourceStarts = 0
        val sr = SharedResource(
            tag = "slowclose",
            parentScope = this + Dispatchers.IO,
            stopTimeout = Duration.ZERO,
            source = callbackFlow {
                val generation = ++sourceStarts
                send(generation)
                awaitClose {
                    // The first generation's teardown is deliberately wedged (like a root host that
                    // refuses to disconnect). It must NOT hold up acquiring a fresh generation.
                    if (generation == 1) runBlocking { releaseTeardown.await() }
                }
            },
        )

        val r1 = sr.get()
        r1.item shouldBe 1
        r1.close() // detaches gen-1; its teardown is now wedged in awaitClose

        // A new get() must start gen-2 and return promptly instead of waiting on gen-1's stuck close.
        val r2 = withTimeout(5_000) { sr.get() }
        r2.item shouldBe 2
        r2.close()

        releaseTeardown.complete(Unit) // let gen-1 finish so the scope can wind down
    }

    @Test fun `get() cancelled before first value releases its provisional lease`(): Unit = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val sr = SharedResource(
            tag = "cancelgate",
            parentScope = this + Dispatchers.IO,
            stopTimeout = Duration.ZERO,
            source = callbackFlow {
                gate.await() // never produces a value until released
                send(Unit)
                awaitClose { }
            },
        )

        val getter = launch(Dispatchers.IO) { sr.get() }
        // Wait until the source generation is active (provisional lease registered).
        withTimeout(5_000) { while (sr.isClosed) delay(10) }

        getter.cancelAndJoin() // cancel before any value arrives

        // The provisional lease must have been released, so the resource detaches on its own.
        withTimeout(5_000) { while (!sr.isClosed) delay(10) }
        sr.isClosed shouldBe true

        gate.complete(Unit)
    }

    @Test fun `a stale generation cannot clobber the active one`(): Unit = runBlocking {
        val releaseGen1 = CompletableDeferred<Unit>()
        var sourceStarts = 0
        val sr = SharedResource(
            tag = "noclobber",
            parentScope = this + Dispatchers.IO,
            stopTimeout = Duration.ZERO,
            source = callbackFlow {
                val generation = ++sourceStarts
                send(if (generation == 1) "A" else "B")
                awaitClose {
                    // gen-1's teardown is wedged until we release it, so its onCompletion fires
                    // *after* gen-2 is already the active generation.
                    if (generation == 1) runBlocking { releaseGen1.await() }
                }
            },
        )

        sr.get().apply { item shouldBe "A" }.close() // detaches gen-1 (teardown wedged)
        val r2 = withTimeout(5_000) { sr.get() }
        r2.item shouldBe "B"

        // Let the stale gen-1 finish tearing down; its onEach/onCompletion must not touch gen-2.
        releaseGen1.complete(Unit)
        delay(250)

        // gen-2 is still healthy: a fresh get() reuses it and returns "B" without an error.
        val r3 = sr.get()
        r3.item shouldBe "B"
        r2.close()
        r3.close()
    }

    @Test fun `source failure is thrown promptly despite a wedged stale teardown`(): Unit = runBlocking {
        val releaseGen1 = CompletableDeferred<Unit>()
        var sourceStarts = 0
        val sr = SharedResource<String>(
            tag = "failfast",
            parentScope = this + Dispatchers.IO,
            stopTimeout = Duration.ZERO,
            source = callbackFlow {
                val generation = ++sourceStarts
                if (generation == 1) {
                    send("A")
                    awaitClose { runBlocking { releaseGen1.await() } }
                } else {
                    throw IllegalStateException("gen-2 boom")
                }
            },
        )

        sr.get().close() // detaches gen-1 (teardown wedged)

        // gen-2's source fails; get() must surface that promptly rather than waiting on gen-1.
        val error = withTimeout(5_000) { shouldThrow<IllegalStateException> { sr.get() } }
        error.message shouldBe "gen-2 boom"

        releaseGen1.complete(Unit)
    }

    @Test fun `a fresh lease cycle completes while a stale teardown is wedged`(): Unit = runBlocking {
        val releaseGen1 = CompletableDeferred<Unit>()
        var sourceStarts = 0
        val sr = SharedResource(
            tag = "wedged",
            parentScope = this + Dispatchers.IO,
            stopTimeout = Duration.ZERO,
            source = callbackFlow {
                val generation = ++sourceStarts
                send(generation)
                awaitClose { if (generation == 1) runBlocking { releaseGen1.await() } }
            },
        )

        sr.get().close() // gen-1 detached, teardown wedged

        // A full acquire + release of gen-2 (incl. its own detach via leaseCheck/doLeaseCheck) must
        // complete promptly — the wedged gen-1 teardown must not hold coreLock or leaseCheckLock.
        withTimeout(5_000) {
            val r = sr.get()
            r.item shouldBe 2
            r.close()
            while (!sr.isClosed) delay(10)
        }
        sr.isClosed shouldBe true

        releaseGen1.complete(Unit)
    }

    @Test fun `close() releases a get() parked on a generation with a wedged teardown`(): Unit = runBlocking {
        val releaseTeardown = CompletableDeferred<Unit>()
        val sr = SharedResource<Int>(
            tag = "wedgedclose",
            parentScope = this + Dispatchers.IO,
            stopTimeout = Duration.ZERO,
            source = callbackFlow {
                // Never produces a value, and its teardown wedges until released — so the only way a
                // parked get() can return is if detach completes `ready`, NOT the source completion.
                awaitClose { runBlocking { releaseTeardown.await() } }
            },
        )

        // A getter parks on the generation's `ready` (source never emits); it holds a lease.
        val getter = async(Dispatchers.IO) { runCatching { sr.get() } }
        withTimeout(5_000) { while (sr.isClosed) delay(10) } // wait until the generation is active

        // close() force-closes the lease and detaches. The parked getter must be released promptly,
        // without waiting for the wedged awaitClose teardown.
        sr.close()

        val result = withTimeout(5_000) { getter.await() }
        result.isFailure shouldBe true

        releaseTeardown.complete(Unit) // let the off-lock teardown finish so the scope can wind down
    }

    @Test fun `a superseded generation's self-teardown never clobbers the live generation`(): Unit = runBlocking {
        repeat(300) {
            val gen1Gate = CompletableDeferred<Unit>()
            var starts = 0
            val sr = SharedResource<Int>(
                tag = "supersede",
                parentScope = this + Dispatchers.IO,
                stopTimeout = Duration.ZERO,
                source = callbackFlow {
                    val g = ++starts
                    send(g)
                    if (g == 1) {
                        gen1Gate.await()
                        throw IllegalStateException("gen-1 spontaneous death")
                    } else {
                        awaitClose()
                    }
                },
            )
            val r1 = sr.get()
            r1.item shouldBe 1
            // Let gen-1 die spontaneously while we install gen-2; its stale onCompletion must NOT tear
            // down the fresh generation.
            val killer = launch(Dispatchers.IO) { gen1Gate.complete(Unit) }
            r1.close()
            val r2 = withTimeout(5_000) {
                var r = sr.get()
                var guard = 0
                while (r.item == 1 && guard++ < 1000) {
                    r.close()
                    r = sr.get()
                }
                r
            }
            r2.item shouldBe 2
            killer.join()
            delay(15)
            r2.isClosed shouldBe false
            sr.isClosed shouldBe false
            r2.close()
            sr.close()
        }
    }

    @Test fun `close() decided under lock converges with a racing cold get()`(): Unit = runBlocking {
        repeat(300) {
            val sr = SharedResource.createKeepAlive("coldclose", this + Dispatchers.Default, Duration.ZERO)
            var acquired: KeepAlive? = null
            val getter = launch(Dispatchers.IO) { acquired = runCatching { sr.get() }.getOrNull() }
            val closer = launch(Dispatchers.IO) { sr.close() }
            joinAll(getter, closer)
            acquired?.close()
            withTimeout(2_000) { while (!sr.isClosed) delay(1) }
            sr.isClosed shouldBe true
        }
    }

    @Test fun `detach is not derailed by a child whose close throws`(): Unit = runBlocking {
        var sourceTornDown = false
        val parent = SharedResource<Int>(
            tag = "throwparent",
            parentScope = this + Dispatchers.IO,
            stopTimeout = Duration.ZERO,
            source = callbackFlow {
                send(1)
                awaitClose { sourceTornDown = true } // fires only if the off-lock source teardown runs
            },
        )
        val keepParent = parent.get().apply { item shouldBe 1 } // keep parent active while we inject

        // A misbehaving child (close throws) inserted BEFORE a well-behaved one. Detach must close the
        // good child despite the bad one, and must still tear the source down.
        var goodChildClosed = false
        val throwingChild = object : KeepAlive {
            override val resourceId = "throwing"
            override val isClosed = false // detachLocked never reads this; close() just throws
            override fun close() = throw RuntimeException("boom on child close")
        }
        val goodChild = object : KeepAlive {
            override val resourceId = "good"
            override val isClosed: Boolean get() = goodChildClosed
            override fun close() {
                goodChildClosed = true
            }
        }
        parent.injectChildForTest(SharedResource.createKeepAlive("k1", this + Dispatchers.IO, Duration.ZERO), throwingChild)
        parent.injectChildForTest(SharedResource.createKeepAlive("k2", this + Dispatchers.IO, Duration.ZERO), goodChild)

        keepParent.close() // drops the only real lease -> detach -> detachLocked closes children

        withTimeout(5_000) {
            while (!(parent.isClosed && goodChildClosed && sourceTornDown)) delay(1)
        }
        parent.isClosed shouldBe true   // detach completed despite the throwing child
        goodChildClosed shouldBe true   // iteration continued past the thrower (per-child runCatching)
        sourceTornDown shouldBe true    // source teardown was enqueued before the child loop, not stranded
    }

    @Test
    fun `get re-acquires a fresh generation when a reused one fails the liveness check`() = runTest2 {
        // Real dispatcher, like the other tests here: SharedResource's Lease.close uses runBlocking, which
        // deadlocks a single-threaded test dispatcher.
        val produced = AtomicInteger(0)
        val liveThreshold = AtomicInteger(0) // resources with id > threshold are considered alive
        val sr = SharedResource<Int>(
            tag = "reuse-liveness",
            parentScope = this + Dispatchers.IO,
            source = flow {
                emit(produced.incrementAndGet())
                awaitCancellation()
            },
            isReusable = { it > liveThreshold.get() },
        )

        // A held lease pins generation 1 as `active`, so no idle-teardown can race this test.
        val pin = sr.get()
        pin.item shouldBe 1

        // Resource 1 is now "dead" WITHOUT completing its source, so `active` still points at generation 1 —
        // exactly the window where SharedResource's async detach hasn't cleared the dead generation yet.
        liveThreshold.set(1)

        // Without the fix, get() reuses the stale dead generation 1. With it, get() fails the liveness
        // check, detaches generation 1, and re-acquires a fresh generation 2.
        val second = sr.get()
        second.item shouldBe 2

        second.close()
        sr.close()
    }

    @Test
    fun `get retries when a concurrent close force-closes the lease mid-acquisition`() = runTest2 {
        // Real dispatcher: Lease.close/close() use runBlocking.
        val produced = AtomicInteger(0)
        val closeTriggered = AtomicInteger(0)
        lateinit var sr: SharedResource<Int>
        sr = SharedResource(
            tag = "close-race",
            parentScope = this + Dispatchers.IO,
            stopTimeout = Duration.ZERO,
            source = flow {
                emit(produced.incrementAndGet())
                awaitCancellation()
            },
            // The validator runs in the window between lease registration (under coreLock) and get()
            // returning — exactly where a concurrent close() can force-close the just-created lease.
            // Trigger that close() deterministically on the first acquisition.
            isReusable = {
                if (closeTriggered.incrementAndGet() == 1) sr.close()
                true
            },
        )

        val resource = sr.get()
        // Without the retry, get() hands back generation 1's force-closed lease and this access throws.
        // With it, get() notices the closed lease and re-acquires a fresh generation.
        resource.item shouldBe 2
        resource.close()
        sr.close()
    }

    @Test
    fun `a reused generation dying before its first value does not poison the reusing caller`(): Unit = runBlocking {
        val attempts = AtomicInteger(0)
        val firstStarted = CompletableDeferred<Unit>()
        val failFirst = CompletableDeferred<Unit>()
        val sr = SharedResource<Int>(
            tag = "stale-reuse",
            parentScope = this + Dispatchers.IO,
            stopTimeout = Duration.ZERO,
            source = flow {
                if (attempts.incrementAndGet() == 1) {
                    firstStarted.complete(Unit)
                    failFirst.await()
                    throw IOException("source died before producing a value")
                }
                emit(attempts.get())
                awaitCancellation()
            },
        )

        val creator = async(Dispatchers.IO) { runCatching { sr.get() } }
        firstStarted.await()
        // The source is running but has produced no value yet, so this get() REUSES generation 1
        // and parks on its ready-gate...
        val reuser = async(Dispatchers.IO) { runCatching { sr.get() } }
        delay(100)
        // ...then generation 1 dies before ever producing a value.
        failFirst.complete(Unit)

        // The caller that STARTED the doomed source sees its original failure...
        (creator.await().exceptionOrNull() is IOException) shouldBe true
        // ...but the caller that merely latched onto the dying generation must not inherit that stale
        // error — it retries onto a fresh generation instead.
        val resource = reuser.await().getOrThrow()
        resource.item shouldBe 2

        resource.close()
        sr.close()
    }
}