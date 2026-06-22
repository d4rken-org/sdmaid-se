package eu.darken.sdmse.common.areas

import eu.darken.sdmse.common.areas.modules.DataAreaFactory
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

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
}
