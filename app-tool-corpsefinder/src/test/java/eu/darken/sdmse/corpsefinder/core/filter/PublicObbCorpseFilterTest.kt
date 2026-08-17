package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.areas.DataArea
import org.junit.jupiter.api.Test
import testhelpers.coroutine.runTest2

class PublicObbCorpseFilterTest : StandardCorpseFilterTest() {

    override val areaType = DataArea.Type.PUBLIC_OBB
    override val filterClass = PublicObbCorpseFilter::class

    // PublicObbCSI has no dirname fallback: an obb dir nobody claims stays ownerless.
    override val defaultPreset = Preset.BlacklistOrphan

    override fun create() = PublicObbCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

    @Test fun `an item with a stale clutter owner is a corpse`() = runTest2 {
        // PublicObbCSI resolves owners from the clutter DB without verifying they are installed.
        assertStaleOwnerIsCorpse()
    }

    @Test fun `an item with an unknown owner is not a corpse`() = runTest2 {
        // PublicObbCSI reports an unknown owner when the obb is currently mounted.
        assertUnknownOwnerIsNotCorpse()
    }

    @Test fun `keeper risk gating`() = runTest2 {
        assertKeeperGating()
    }

    @Test fun `common risk gating`() = runTest2 {
        assertCommonGating()
    }

    @Test fun `scans without root below API 33`() = runTest2 {
        fakeSdk(32)
        hasRoot(false)
        hasAdb(false)

        assertScans()
    }

    @Test fun `scans with root on API 33`() = runTest2 {
        fakeSdk(33)
        hasRoot(true)
        hasAdb(false)

        assertScans()
    }

    @Test fun `skips without root on API 33 even with adb`() = runTest2 {
        fakeSdk(33)
        hasRoot(false)
        hasAdb(true)

        assertSkipsScan()
    }

    @Test fun `still scans on untested API levels`() = runTest2 {
        fakeSdk(37)

        assertScans()
    }

    @Test fun `nomedia is excluded by name`() = runTest2 {
        assertNameExcluded(".nomedia")
    }
}
