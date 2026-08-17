package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.areas.DataArea
import org.junit.jupiter.api.Test
import testhelpers.coroutine.runTest2

class PrivateDataCorpseFilterTest : StandardCorpseFilterTest() {

    override val areaType = DataArea.Type.PRIVATE_DATA
    override val filterClass = PrivateDataCorpseFilter::class

    // PrivateDataCSI only leaves the owner set empty when it also flags an unknown owner, otherwise
    // it falls back to dirname=pkgname. An ownerless corpse without that flag cannot happen.
    override val defaultPreset = Preset.StaleOwner()

    override fun create() = PrivateDataCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

    @Test fun `an item with an unknown owner is not a corpse`() = runTest2 {
        // PrivateDataCSI reports an unknown owner when the uid maps to a shared or unresolvable owner.
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

    @Test fun `hosts is excluded by name`() = runTest2 {
        assertNameExcluded("hosts")
    }

    @Test fun `lost+found is excluded by name`() = runTest2 {
        assertNameExcluded("lost+found")
    }
}
