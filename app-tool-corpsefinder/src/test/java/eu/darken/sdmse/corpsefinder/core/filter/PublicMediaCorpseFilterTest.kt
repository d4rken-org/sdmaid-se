package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.areas.DataArea
import org.junit.jupiter.api.Test
import testhelpers.coroutine.runTest2

class PublicMediaCorpseFilterTest : StandardCorpseFilterTest() {

    override val areaType = DataArea.Type.PUBLIC_MEDIA
    override val filterClass = PublicMediaCorpseFilter::class

    // PublicMediaCSI falls back to dirname=pkgname, so it always names an owner and never reports an
    // unknown one. Ownerless public media cannot happen.
    override val defaultPreset = Preset.StaleOwner()

    override fun create() = PublicMediaCorpseFilter(
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

    @Test fun `scans without root or adb`() = runTest2 {
        hasRoot(false)
        hasAdb(false)

        assertScans()
    }

    @Test fun `still scans on untested API levels`() = runTest2 {
        fakeSdk(37)
        hasRoot(false)
        hasAdb(false)

        assertScans()
    }

    @Test fun `nomedia is excluded by name`() = runTest2 {
        assertNameExcluded(".nomedia")
    }
}
