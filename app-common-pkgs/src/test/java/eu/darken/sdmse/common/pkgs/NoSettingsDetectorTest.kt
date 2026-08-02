package eu.darken.sdmse.common.pkgs

import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.device.RomTypeProvider
import eu.darken.sdmse.common.pkgs.container.NormalPkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class NoSettingsDetectorTest : BaseTest() {

    @MockK lateinit var deviceDetective: DeviceDetective
    @MockK lateinit var romTypeProvider: RomTypeProvider

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        coEvery { romTypeProvider.getRomType() } returns RomType.AUTO
    }

    private fun create() = NoSettingsDetector(
        deviceDetective = deviceDetective,
        romTypeProvider = romTypeProvider,
    )

    private fun mockPkg(
        enabled: Boolean = true,
        hasNoSettings: Boolean = false,
    ): NormalPkg = mockk<NormalPkg>().apply {
        val pkgId = "some.pkg".toPkgId()
        every { id } returns pkgId
        every { installId } returns InstallId(pkgId, UserHandle2(handleId = 0))
        // MockK intercepts the getters, the InstallDetails/Installed default impls never run
        every { isEnabled } returns enabled
        every { this@apply.hasNoSettings } returns hasNoSettings
    }

    @Test
    fun `structurally unreachable packages are flagged on any ROM`() = runTest {
        every { deviceDetective.getROMType() } returns RomType.AOSP

        create().getUnreachableReason(mockPkg(hasNoSettings = true)) shouldBe
                NoSettingsDetector.Reason.NO_SETTINGS_PAGE
    }

    @Test
    fun `disabled apps are unreachable on One UI`() = runTest {
        every { deviceDetective.getROMType() } returns RomType.ONEUI

        create().getUnreachableReason(mockPkg(enabled = false)) shouldBe
                NoSettingsDetector.Reason.DISABLED_APP
    }

    @Test
    fun `enabled apps are reachable on One UI`() = runTest {
        every { deviceDetective.getROMType() } returns RomType.ONEUI

        create().getUnreachableReason(mockPkg(enabled = true)) shouldBe null
    }

    @Test
    fun `disabled apps are reachable on other ROMs`() = runTest {
        every { deviceDetective.getROMType() } returns RomType.AOSP

        create().getUnreachableReason(mockPkg(enabled = false)) shouldBe null
    }

    @Test
    fun `manual One UI override applies the rule on an undetected ROM`() = runTest {
        every { deviceDetective.getROMType() } returns RomType.AOSP
        coEvery { romTypeProvider.getRomType() } returns RomType.ONEUI

        create().getUnreachableReason(mockPkg(enabled = false)) shouldBe
                NoSettingsDetector.Reason.DISABLED_APP
    }

    @Test
    fun `manual non-One UI override drops the rule on a detected Samsung`() = runTest {
        every { deviceDetective.getROMType() } returns RomType.ONEUI
        coEvery { romTypeProvider.getRomType() } returns RomType.AOSP

        create().getUnreachableReason(mockPkg(enabled = false)) shouldBe null
    }

    @Test
    fun `structural check wins over the disabled check`() = runTest {
        every { deviceDetective.getROMType() } returns RomType.ONEUI

        create().getUnreachableReason(mockPkg(enabled = false, hasNoSettings = true)) shouldBe
                NoSettingsDetector.Reason.NO_SETTINGS_PAGE
    }
}
