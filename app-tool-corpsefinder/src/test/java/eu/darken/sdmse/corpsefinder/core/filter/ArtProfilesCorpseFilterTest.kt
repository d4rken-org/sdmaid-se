package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.areas.DataArea
import org.junit.jupiter.api.Test
import testhelpers.coroutine.runTest2

class ArtProfilesCorpseFilterTest : StandardCorpseFilterTest() {

    override val areaType = DataArea.Type.ART_PROFILE
    override val filterClass = ArtProfilesCorpseFilter::class

    // ArtProfileCSI runs a single DirNameCheck: an owner exists only when that package is installed.
    // So the only corpse shape it can produce is "no owners at all" - a stale owner, an unknown
    // owner and clutter flags (keeper/common) are all unreachable here, hence no such tests below.
    override val defaultPreset = Preset.BlacklistOrphan

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

    @Test fun `scans but withholds on API 37`() = runTest2 {
        fakeSdk(37)

        assertWithholdsScan()
    }
}
