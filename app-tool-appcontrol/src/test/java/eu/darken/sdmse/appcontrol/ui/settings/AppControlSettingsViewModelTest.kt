package eu.darken.sdmse.appcontrol.ui.settings

import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppControlSettings
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.UpgradeRepo.Info
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import testhelpers.mockDataStoreValue

class AppControlSettingsViewModelTest : BaseTest() {

    private fun vm(
        isPro: Boolean = true,
        sizingEnabled: Boolean = true,
        activityEnabled: Boolean = true,
        multiUserEnabled: Boolean = false,
        canInfoSize: Boolean = true,
        canInfoActive: Boolean = true,
        canIncludeMultiUser: Boolean = true,
    ): AppControlSettingsViewModel {
        val settings = mockk<AppControlSettings>().apply {
            coEvery { moduleSizingEnabled } returns mockDataStoreValue(sizingEnabled)
            coEvery { moduleActivityEnabled } returns mockDataStoreValue(activityEnabled)
            coEvery { includeMultiUserEnabled } returns mockDataStoreValue(multiUserEnabled)
            coEvery { listSort } returns mockDataStoreValue(mockk(relaxed = true))
            coEvery { listFilter } returns mockDataStoreValue(mockk(relaxed = true))
        }
        val appControl = mockk<AppControl>().apply {
            every { state } returns flowOf(
                AppControl.State(
                    data = null,
                    progress = null as Progress.Data?,
                    canInfoActive = canInfoActive,
                    canInfoSize = canInfoSize,
                    canInfoScreenTime = false,
                    canToggle = false,
                    canForceStop = false,
                    canIncludeMultiUser = canIncludeMultiUser,
                    canArchive = false,
                    canRestore = false,
                ),
            )
        }
        val upgradeRepo = mockk<UpgradeRepo>().apply {
            val info = mockk<Info>().apply { every { this@apply.isPro } returns isPro }
            every { upgradeInfo } returns MutableStateFlow(info)
        }
        return AppControlSettingsViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            appControl = appControl,
            upgradeRepo = upgradeRepo,
            settings = settings,
        )
    }

    @Test
    fun `state reflects pro-capable ready environment`() = runTest2 {
        val state = vm().state.first()
        state.isPro shouldBe true
        state.canInfoSize shouldBe true
        state.canIncludeMultiUser shouldBe true
    }

    @Test
    fun `state reflects non-pro with no setup capabilities`() = runTest2 {
        val state = vm(
            isPro = false,
            canInfoSize = false,
            canInfoActive = false,
            canIncludeMultiUser = false,
        ).state.first()
        state.isPro shouldBe false
        state.canInfoSize shouldBe false
        state.canInfoActive shouldBe false
        state.canIncludeMultiUser shouldBe false
    }
}
