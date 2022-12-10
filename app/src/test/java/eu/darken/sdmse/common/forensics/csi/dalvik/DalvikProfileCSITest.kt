package eu.darken.sdmse.common.forensics.csi.dalvik

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.forensics.csi.dalvik.tools.DalvikClutterCheck
import eu.darken.sdmse.common.forensics.csi.dalvik.tools.DirNameCheck
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.randomString
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DalvikProfileCSITest : BaseCSITest() {

    private val dalvikCachesBases = listOf(
        LocalPath.build("/data/dalvik-cache"),
        LocalPath.build("/cache/dalvik-cache"),
    )

    private val areaProfile1 = mockk<DataArea>().apply {
        every { type } returns DataArea.Type.DALVIK_PROFILE
        every { path } returns LocalPath.build(dalvikCachesBases[0], "profiles")
        every { flags } returns emptySet()
    }
    private val areaProfile2 = mockk<DataArea>().apply {
        every { type } returns DataArea.Type.DALVIK_PROFILE
        every { path } returns LocalPath.build(dalvikCachesBases[1], "profiles")
        every { flags } returns emptySet()
    }

    private val profilePaths = setOf(
        areaProfile1.path,
        areaProfile2.path
    ).map { it as LocalPath }

    @Before override fun setup() {
        super.setup()

        every { areaManager.areas } returns flowOf(
            setOf(
                areaProfile1,
                areaProfile2,
            )
        )
    }

    private fun getProcessor() = DalvikProfileCSI(
        areaManager = areaManager,
        dirNameCheck = DirNameCheck(pkgRepo),
        clutterCheck = DalvikClutterCheck(clutterRepo),
    )

    @Test override fun `test jurisdiction`() = runTest {
        getProcessor().assertJurisdiction(DataArea.Type.DALVIK_PROFILE)
    }

    @Test override fun `determine area successfully`() = runTest {
        val processor = getProcessor()

        for (profilePath in profilePaths) {
            val testFile1 = LocalPath.build(profilePath, randomString())
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.DALVIK_PROFILE
                prefix shouldBe "${profilePath.path}/"
                prefixFreePath shouldBe testFile1.name
                isBlackListLocation shouldBe true
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()

        processor.identifyArea(LocalPath.build("/data/dalvik-cache", randomString())) shouldBe null
        processor.identifyArea(LocalPath.build("/data/dalvik-cache/arm", randomString())) shouldBe null
        processor.identifyArea(LocalPath.build("/cache/dalvik-cache")) shouldBe null
        processor.identifyArea(LocalPath.build("/cache/dalvik-cache/arm64")) shouldBe null
    }

    @Test fun `find default owner`() = runTest {
        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        for (base in profilePaths) {
            val toHit = LocalPath.build(base, "eu.thedarken.sdm.test")
            val locationInfo = getProcessor().identifyArea(toHit)!!

            getProcessor().findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
                hasKnownUnknownOwner shouldBe false
            }
        }

        for (base in profilePaths) {
            val toHit = LocalPath.build(base, "eu.thedarken.sdm.test/abc/def")
            val locationInfo = getProcessor().identifyArea(toHit)!!.apply {
                prefix shouldBe "${base.path}/"
            }

            getProcessor().findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
                hasKnownUnknownOwner shouldBe false
            }
        }
    }

    @Test fun `find owner via direct clutter hit`() = runTest {
        val processor = getProcessor()
        val pkgId = "com.test.pkg".toPkgId()

        val prefixFree = randomString()
        mockMarker(pkgId, DataArea.Type.DALVIK_PROFILE, prefixFree)

        for (base in profilePaths) {
            val areaInfo = processor.identifyArea(LocalPath.build(base, prefixFree))!!.apply {
                prefix shouldBe "${base.path}/"
            }

            processor.findOwners(areaInfo).apply {
                owners.single().pkgId shouldBe pkgId
            }
        }
    }

    @Test fun `find no owner or fallback`() = runTest {
        val processor = getProcessor()

        for (profile in profilePaths) {
            val testFile1 = LocalPath.build(profile, randomString())
            val areaInfo = processor.identifyArea(testFile1)!!

            processor.findOwners(areaInfo).apply {
                owners shouldBe emptySet()
                hasKnownUnknownOwner shouldBe false
            }
        }
    }
}