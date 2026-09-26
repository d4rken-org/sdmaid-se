package eu.darken.sdmse.common.areas

import eu.darken.sdmse.common.areas.modules.DataAreaFactory
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import kotlin.time.Duration.Companion.seconds

@ExtendWith(MockKExtension::class)
class DataAreaManagerTest : BaseTest() {

    @MockK lateinit var areaFactory: DataAreaFactory

    @BeforeEach
    fun setup() {
        coEvery { areaFactory.build() } returns emptySet()
    }

    @Test
    fun `reloadAndAwait completes after refreshed state is emitted`() = runTest2(autoCancel = true) {
        val reloadBuildStarted = CompletableDeferred<Unit>()
        val reloadBuildAllowed = CompletableDeferred<Unit>()
        var buildCount = 0
        coEvery { areaFactory.build() } coAnswers {
            buildCount += 1
            if (buildCount == 2) {
                reloadBuildStarted.complete(Unit)
                reloadBuildAllowed.await()
            }
            emptySet()
        }

        val manager = DataAreaManager(
            appScope = this,
            areaFactory = areaFactory,
        )

        manager.state.first().refreshGeneration shouldBe 0L

        val reloadedState = async { manager.reloadAndAwait() }
        reloadBuildStarted.await()

        reloadedState.isActive shouldBe true

        reloadBuildAllowed.complete(Unit)
        reloadedState.await().refreshGeneration shouldBe 1L

        coVerify(exactly = 2) { areaFactory.build() }
    }

    @Test
    fun `reading areas while a reload is still building waits for it`() = runTest2(autoCancel = true) {
        val reloadBuildAllowed = CompletableDeferred<Unit>()
        var buildCount = 0
        coEvery { areaFactory.build() } coAnswers {
            buildCount += 1
            if (buildCount == 2) {
                reloadBuildAllowed.await()
                setOf(areaAfter)
            } else {
                setOf(areaBefore)
            }
        }
        val manager = DataAreaManager(appScope = this, areaFactory = areaFactory)
        manager.currentAreas() shouldBe setOf(areaBefore)

        manager.reload()
        val reading = async { manager.currentAreas() }
        testScheduler.runCurrent()
        reading.isCompleted shouldBe false

        reloadBuildAllowed.complete(Unit)
        reading.await() shouldBe setOf(areaAfter)
    }

    @Test
    fun `a reload during the initial build replaces it`() = runTest2(autoCancel = true) {
        val initialBuildAllowed = CompletableDeferred<Unit>()
        var buildCount = 0
        coEvery { areaFactory.build() } coAnswers {
            buildCount += 1
            if (buildCount == 1) {
                initialBuildAllowed.await()
                setOf(areaBefore)
            } else {
                setOf(areaAfter)
            }
        }
        val manager = DataAreaManager(appScope = this, areaFactory = areaFactory)
        val initialRead = async { manager.currentAreas() }
        testScheduler.runCurrent()

        manager.reload()
        initialBuildAllowed.complete(Unit)

        initialRead.await() shouldBe setOf(areaAfter)
        manager.currentAreas() shouldBe setOf(areaAfter)
    }

    @Test
    fun `a collector gets the newest areas after a superseded reload`() = runTest2(autoCancel = true) {
        val secondBuildStarted = CompletableDeferred<Unit>()
        var buildCount = 0
        coEvery { areaFactory.build() } coAnswers {
            buildCount += 1
            when (buildCount) {
                1 -> setOf(areaBefore)
                2 -> {
                    secondBuildStarted.complete(Unit)
                    awaitCancellation()
                }

                else -> setOf(areaAfter)
            }
        }
        val manager = DataAreaManager(appScope = this, areaFactory = areaFactory)
        val seen = mutableListOf<Set<DataArea>>()
        backgroundScope.launch { manager.state.collect { seen.add(it.areas) } }
        testScheduler.runCurrent()

        manager.reload()
        secondBuildStarted.await()
        manager.reload()
        testScheduler.runCurrent()

        seen shouldBe listOf(setOf(areaBefore), setOf(areaAfter))
    }

    @Test
    fun `a build failing from a cancellation reaches its readers`() =
        runTest2(autoCancel = true, timeout = 10.seconds) {
            var buildCount = 0
            coEvery { areaFactory.build() } coAnswers {
                buildCount += 1
                when (buildCount) {
                    1 -> setOf(areaBefore)
                    2 -> throw IllegalStateException("Gateway lost", CancellationException("Disconnected"))
                    else -> setOf(areaAfter)
                }
            }
            val manager = DataAreaManager(appScope = this, areaFactory = areaFactory)
            manager.currentAreas() shouldBe setOf(areaBefore)

            manager.reload()
            shouldThrow<IllegalStateException> { manager.currentAreas() }.message shouldBe "Gateway lost"

            manager.reload()
            manager.currentAreas() shouldBe setOf(areaAfter)
        }

    @Test
    fun `latestResult reports a failed build while latestState keeps the last success`() =
        runTest2(autoCancel = true, timeout = 10.seconds) {
            var buildCount = 0
            coEvery { areaFactory.build() } coAnswers {
                buildCount += 1
                when (buildCount) {
                    2 -> throw IllegalStateException("Gateway lost", CancellationException("Disconnected"))
                    else -> setOf(areaBefore)
                }
            }
            val manager = DataAreaManager(appScope = this, areaFactory = areaFactory)
            manager.currentAreas()

            manager.reload()
            manager.results.first { it.isFailure }
            manager.latestResult.first()!!.isFailure shouldBe true
            manager.latestState.first()!!.areas shouldBe setOf(areaBefore)

            manager.reload()
            manager.currentAreas()
            manager.latestResult.first()!!.getOrNull()!!.areas shouldBe setOf(areaBefore)
        }

    @Test
    fun `a build throwing a bare cancellation reaches its readers as a failure`() =
        runTest2(autoCancel = true, timeout = 10.seconds) {
            var buildCount = 0
            coEvery { areaFactory.build() } coAnswers {
                buildCount += 1
                if (buildCount == 2) throw CancellationException("Timed out") else setOf(areaBefore)
            }
            val manager = DataAreaManager(appScope = this, areaFactory = areaFactory)
            manager.currentAreas()

            manager.reload()
            val error = shouldThrow<IllegalStateException> { manager.currentAreas() }
            (error is CancellationException) shouldBe false
            error.cause!!.message shouldBe "Timed out"
        }

    @Test
    fun `an ordinary build failure still escapes to the app scope`() = runTest2(timeout = 10.seconds) {
        val escaped = CompletableDeferred<Throwable>()
        val appScope = CoroutineScope(
            SupervisorJob() +
                StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, e -> escaped.complete(e) },
        )
        var buildCount = 0
        coEvery { areaFactory.build() } coAnswers {
            buildCount += 1
            if (buildCount == 2) throw IllegalArgumentException("boom") else setOf(areaBefore)
        }
        val manager = DataAreaManager(appScope = appScope, areaFactory = areaFactory)
        manager.currentAreas()

        manager.reload()

        escaped.await().message shouldBe "boom"
        appScope.cancel()
    }

    companion object {
        private val areaBefore = DataArea(
            type = DataArea.Type.PRIVATE_DATA,
            path = LocalPath.build("data", "data"),
            userHandle = UserHandle2(0),
        )
        private val areaAfter = DataArea(
            type = DataArea.Type.SDCARD,
            path = LocalPath.build("storage", "emulated", "0"),
            userHandle = UserHandle2(0),
        )
    }
}
