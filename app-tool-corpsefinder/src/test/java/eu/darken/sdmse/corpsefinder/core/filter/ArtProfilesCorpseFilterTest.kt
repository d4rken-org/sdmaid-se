package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.areas.DataArea
import org.junit.jupiter.api.Test
import testhelpers.coroutine.runTest2

class ArtProfilesCorpseFilterTest : StandardCorpseFilterTest() {

    override val areaType = DataArea.Type.ART_PROFILE
    override val filterClass = ArtProfilesCorpseFilter::class

    override fun create() = ArtProfilesCorpseFilter(
        areaManager = areaManager,
        gatewaySwitch = gatewaySwitch,
        fileForensics = fileForensics,
        corpseFinderSettings = corpseFinderSettings,
        exclusionManager = exclusionManager,
    )

    @Test fun `without root nothing is scanned`() = runTest2 {
        hasRoot(false)
        hasAdb(true)

        assertSkipsScan()
    }

    @Test fun `scans on API 36`() = runTest2 {
        fakeSdk(36)

        assertScans()
    }

    @Test fun `bails on API 37`() = runTest2 {
        fakeSdk(37)

        assertSkipsScan()
    }
}
