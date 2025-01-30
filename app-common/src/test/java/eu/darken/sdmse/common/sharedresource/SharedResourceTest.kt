package eu.darken.sdmse.common.sharedresource

import eu.darken.sdmse.common.debug.Bugs
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
            val jobs = mutableSetOf<Job>()

            launch(Dispatchers.IO) {
                val leadLease = sr.get()

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