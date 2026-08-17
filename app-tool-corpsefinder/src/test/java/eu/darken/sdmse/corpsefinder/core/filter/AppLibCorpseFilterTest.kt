package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.areas.DataArea
import org.junit.jupiter.api.Test
import testhelpers.coroutine.runTest2

class AppLibCorpseFilterTest : StandardCorpseFilterTest() {

    override val areaType = DataArea.Type.APP_LIB
    override val filterClass = AppLibCorpseFilter::class

    override fun create() = AppLibCorpseFilter(
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

    @Test fun `scans on API 34`() = runTest2 {
        fakeSdk(34)

        assertScans()
    }

    @Test fun `bails on API 35`() = runTest2 {
        fakeSdk(35)

        assertSkipsScan()
    }
}
