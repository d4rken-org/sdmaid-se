package eu.darken.sdmse.appcleaner.core.automation.specs.hyperos

import android.content.Context
import eu.darken.sdmse.appcleaner.core.automation.specs.BaseAppCleanerSpecTest
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels
import eu.darken.sdmse.automation.core.animation.AnimationState
import eu.darken.sdmse.automation.core.animation.AnimationTool
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.deviceadmin.DeviceAdminManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

/**
 * Test class for HyperOsSpecs.
 *
 * The basic isResponsible() tests are inherited from BaseAppCleanerSpecTest.
 *
 * NOTE: The HyperOS security center plan has complex multi-step dialog flows that use:
 * - Android framework classes (Rect, etc.) that are not available in plain unit tests
 * - Complex event-based window detection with animation settling
 * - Multiple sub-plans with debounce timing logic
 *
 * Testing the full multi-step dialog flow requires either:
 * - Robolectric for Android framework mocking
 * - Integration/UI tests that run on a real device or emulator
 *
 * For now, this test class covers the basic isResponsible() logic from the base class.
 * The complex flow tests should be added as integration tests or with Robolectric.
 */
class HyperOsSpecsTest : BaseAppCleanerSpecTest<HyperOsSpecs, HyperOsLabels>() {

    override val romType = RomType.HYPEROS

    private lateinit var context: Context
    private lateinit var aospLabels: AOSPLabels
    private lateinit var deviceAdminManager: DeviceAdminManager
    private lateinit var animationTool: AnimationTool

    override fun createLabels(): HyperOsLabels {
        // Initialize additional mocks before labels are used
        context = mockk(relaxed = true)
        aospLabels = mockk()
        deviceAdminManager = mockk()
        animationTool = mockk()

        // Default device admin returns empty set
        coEvery { deviceAdminManager.getDeviceAdmins() } returns emptySet()

        // Default animation state (non-disabled)
        coEvery { animationTool.getState() } returns AnimationState(1f, 1f, 1f)

        // Mock context.packageManager for version checks
        every { context.packageManager } returns mockk(relaxed = true)

        return mockk()
    }

    override fun createSpec() = HyperOsSpecs(
        context = context,
        ipcFunnel = ipcFunnel,
        deviceDetective = deviceDetective,
        hyperOsLabels = labels,
        aospLabels = aospLabels,
        deviceAdminManager = deviceAdminManager,
        storageEntryFinder = storageEntryFinder,
        generalSettings = generalSettings,
        stepper = stepper,
        animationTool = animationTool,
    )

    override fun mockLabelDefaults() {
        // HyperOS labels
        every { labels.getClearDataButtonLabels(any()) } returns setOf("Clear data")
        every { labels.getClearCacheButtonLabels(any()) } returns setOf("Clear cache")
        every { labels.getDialogTitles(any()) } returns setOf("Clear cache?")
        every { labels.getManageSpaceButtonLabels(any()) } returns setOf("Manage space")

        // AOSP labels (used in settingsPlan fallback)
        every { aospLabels.getStorageEntryDynamic(any()) } returns emptySet()
        every { aospLabels.getStorageEntryStatic(any()) } returns setOf("Storage")
        every { aospLabels.getClearCacheDynamic(any()) } returns emptySet()
        every { aospLabels.getClearCacheStatic(any()) } returns setOf("Clear cache")
    }

    // NOTE: HyperOS security center plan tests have been removed because they require:
    // - Android framework classes (Rect) for window settling logic
    // - Complex event-based window detection with animation debouncing
    // These tests should be implemented as Robolectric or integration tests.
    //
    // The basic isResponsible() tests are inherited from BaseAppCleanerSpecTest and work correctly.
}
