package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.areas.DataArea
import org.junit.jupiter.api.Test
import testhelpers.coroutine.runTest2

class PublicDataCorpseFilterTest : StandardCorpseFilterTest() {

    override val areaType = DataArea.Type.PUBLIC_DATA
    override val filterClass = PublicDataCorpseFilter::class

    // PublicDataCSI falls back to dirname=pkgname, so it always names an owner and never reports an
    // unknown one. Ownerless public data cannot happen.
    override val defaultPreset = Preset.StaleOwner()

    override fun create() = PublicDataCorpseFilter(
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

    @Test fun `scans without root or adb below API 33`() = runTest2 {
        fakeSdk(32)
        hasRoot(false)
        hasAdb(false)

        assertScans()
    }

    @Test fun `scans with root only on API 33`() = runTest2 {
        fakeSdk(33)
        hasRoot(true)
        hasAdb(false)

        assertScans()
    }

    @Test fun `scans with adb only on API 33`() = runTest2 {
        fakeSdk(33)
        hasRoot(false)
        hasAdb(true)

        assertScans()
    }

    @Test fun `skips without root and adb on API 33`() = runTest2 {
        fakeSdk(33)
        hasRoot(false)
        hasAdb(false)

        assertSkipsScan()
    }

    @Test fun `still scans on untested API levels`() = runTest2 {
        fakeSdk(37)

        assertScans()
    }

    @Test fun `nomedia is excluded by name`() = runTest2 {
        assertNameExcluded(".nomedia")
    }

    @Test fun `hosts is excluded by name`() = runTest2 {
        assertNameExcluded("hosts")
    }

    @Test fun `lost+found is excluded by name`() = runTest2 {
        assertNameExcluded("lost+found")
    }
}
