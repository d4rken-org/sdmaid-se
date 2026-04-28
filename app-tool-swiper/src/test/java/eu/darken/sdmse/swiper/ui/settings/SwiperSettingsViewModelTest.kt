package eu.darken.sdmse.swiper.ui.settings

import eu.darken.sdmse.swiper.core.SwiperSettings
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import testhelpers.mockDataStoreValue

class SwiperSettingsViewModelTest : BaseTest() {

    private fun vm(
        swap: Boolean = false,
        details: Boolean = true,
        haptic: Boolean = true,
    ): SwiperSettingsViewModel {
        val settings = mockk<SwiperSettings>().apply {
            coEvery { swapSwipeDirections } returns mockDataStoreValue(swap)
            coEvery { showFileDetailsOverlay } returns mockDataStoreValue(details)
            coEvery { hapticFeedbackEnabled } returns mockDataStoreValue(haptic)
        }
        return SwiperSettingsViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            settings = settings,
        )
    }

    @Test
    fun `state reflects default DataStore values`() = runTest2 {
        val state = vm().state.first()
        state.swapSwipeDirections shouldBe false
        state.showFileDetailsOverlay shouldBe true
        state.hapticFeedbackEnabled shouldBe true
    }

    @Test
    fun `state reflects non-default DataStore values`() = runTest2 {
        val state = vm(swap = true, details = false, haptic = false).state.first()
        state.swapSwipeDirections shouldBe true
        state.showFileDetailsOverlay shouldBe false
        state.hapticFeedbackEnabled shouldBe false
    }
}
