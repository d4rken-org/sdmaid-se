package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.areas.DataArea
import org.junit.jupiter.api.Test
import testhelpers.coroutine.runTest2

class PrivateDataCorpseFilterTest : StandardCorpseFilterTest() {

    override val areaType = DataArea.Type.PRIVATE_DATA
    override val filterClass = PrivateDataCorpseFilter::class

    override fun create() = PrivateDataCorpseFilter(
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

    @Test fun `hosts is excluded by name`() = runTest2 {
        assertNameExcluded("hosts")
    }

    @Test fun `lost+found is excluded by name`() = runTest2 {
        assertNameExcluded("lost+found")
    }
}
