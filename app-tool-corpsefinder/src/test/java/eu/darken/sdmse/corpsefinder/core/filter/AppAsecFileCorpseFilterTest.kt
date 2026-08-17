package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.areas.DataArea
import org.junit.jupiter.api.Test
import testhelpers.coroutine.runTest2

class AppAsecFileCorpseFilterTest : StandardCorpseFilterTest() {

    override val areaType = DataArea.Type.APP_ASEC
    override val filterClass = AppAsecFileCorpseFilter::class

    // AppSourceAsecCSI always ends up with owners: if neither the asec filename nor the clutter DB
    // resolves one, it adds the file name itself as owner. An ownerless asec corpse cannot happen.
    override val defaultPreset = Preset.StaleOwner()

    override fun create() = AppAsecFileCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

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

    @Test fun `scans on API 34`() = runTest2 {
        fakeSdk(34)

        assertScans()
    }

    @Test fun `bails on API 35`() = runTest2 {
        fakeSdk(35)

        assertSkipsScan()
    }
}
