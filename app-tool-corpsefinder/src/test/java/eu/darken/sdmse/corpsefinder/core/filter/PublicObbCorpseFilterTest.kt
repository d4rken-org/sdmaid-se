package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.areas.DataArea
import org.junit.jupiter.api.Test
import testhelpers.coroutine.runTest2

class PublicObbCorpseFilterTest : StandardCorpseFilterTest() {

    override val areaType = DataArea.Type.PUBLIC_OBB
    override val filterClass = PublicObbCorpseFilter::class

    override fun create() = PublicObbCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

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
