package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.areas.DataArea
import org.junit.jupiter.api.Test
import testhelpers.coroutine.runTest2

class AppLibCorpseFilterTest : StandardCorpseFilterTest() {

    override val areaType = DataArea.Type.APP_LIB
    override val filterClass = AppLibCorpseFilter::class

    // AppSourceLibCSI leaves the owner set empty when neither the dirname, a native library dir nor
    // the clutter DB claims the directory.
    override val defaultPreset = Preset.BlacklistOrphan

    override fun create() = AppLibCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

    @Test fun `an item with a stale clutter owner is a corpse`() = runTest2 {
        // AppSourceLibCSI falls back to clutter matches, which are not install-verified.
        assertStaleOwnerIsCorpse()
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

    @Test fun `scans on API 34`() = runTest2 {
        fakeSdk(34)

        assertScans()
    }

    @Test fun `bails on API 35`() = runTest2 {
        fakeSdk(35)

        assertSkipsScan()
    }
}
