package eu.darken.sdmse.common.upgrade.ui

import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.common.upgrade.core.OurSku
import eu.darken.sdmse.common.upgrade.core.UpgradeRepoGplay
import eu.darken.sdmse.common.upgrade.core.billing.GplayServiceUnavailableException
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class GplayUpgradeViewModelTest : BaseTest() {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `service timeout becomes unavailable state and error event instead of crashing`() = runTest2(
        context = testDispatcher,
    ) {
        val upgradeInfo = MutableStateFlow(UpgradeRepoGplay.Info(false, null, null))
        val repo = mockk<UpgradeRepoGplay>(relaxed = true)
        every { repo.upgradeInfo } returns upgradeInfo
        coEvery { repo.querySkus(OurSku.Iap.PRO_UPGRADE) } coAnswers {
            delay(6_000)
            emptyList()
        }
        coEvery { repo.querySkus(OurSku.Sub.PRO_UPGRADE) } coAnswers {
            delay(6_000)
            emptyList()
        }

        val vm = UpgradeViewModel(
            handle = SavedStateHandle(mapOf("forced" to false)),
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            upgradeRepo = repo,
        )

        val unavailableState = async {
            vm.state.first { it is GplayUpgradeUiState.Unavailable }
        }
        val forwardedError = async { vm.errorEvents.first() }

        advanceUntilIdle()

        unavailableState.await().shouldBeInstanceOf<GplayUpgradeUiState.Unavailable>()
        forwardedError.await().shouldBeInstanceOf<GplayServiceUnavailableException>()
        vm.state.value.shouldBeInstanceOf<GplayUpgradeUiState.Unavailable>()

        coVerify(exactly = 1) { repo.querySkus(OurSku.Iap.PRO_UPGRADE) }
        coVerify(exactly = 1) { repo.querySkus(OurSku.Sub.PRO_UPGRADE) }
    }
}
