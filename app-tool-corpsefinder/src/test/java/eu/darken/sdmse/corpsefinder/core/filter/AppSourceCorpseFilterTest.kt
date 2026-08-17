package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.areas.DataArea
import org.junit.jupiter.api.Test
import testhelpers.coroutine.runTest2

class AppSourceCorpseFilterTest : StandardCorpseFilterTest() {

    override val areaType = DataArea.Type.APP_APP
    override val filterClass = AppSourceCorpseFilter::class

    // AppSourceMainCSI aggregates its checks and can come back with no owner at all.
    override val defaultPreset = Preset.BlacklistOrphan

    override fun create() = AppSourceCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

    @Test fun `an item with a stale clutter owner is a corpse`() = runTest2 {
        // AppSourceClutterCheck contributes owners that were never install-verified.
        assertStaleOwnerIsCorpse()
    }

    @Test fun `an item with an unknown owner is not a corpse`() = runTest2 {
        // The source checks can report hasKnownUnknownOwner.
        assertUnknownOwnerIsNotCorpse()
    }

    @Test fun `keeper risk gating`() = runTest2 {
        assertKeeperGating()
    }

    @Test fun `common risk gating`() = runTest2 {
        assertCommonGating()
    }

    @Test fun `without root nothing is scanned`() = runTest2 {
        hasRoot(false)
        hasAdb(true)

        assertSkipsScan()
    }

    @Test fun `scans on API 36`() = runTest2 {
        fakeSdk(36)

        assertScans()
    }

    @Test fun `scans but withholds on API 37`() = runTest2 {
        fakeSdk(37)

        assertWithholdsScan()
    }
}
