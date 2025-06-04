package eu.darken.sdmse.common.forensics.csi.dalvik

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.removePrefix
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.forensics.csi.dalvik.tools.DirNameCheck
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ArtProfileCSITest : BaseCSITest() {

    private val areaProfile1 = DataArea(
        type = DataArea.Type.ART_PROFILE,
        path = LocalPath.build("data", "misc", "profiles", "ref"),
        userHandle = UserHandle2(-1),
    )
    private val areaProfile2 = DataArea(
        type = DataArea.Type.ART_PROFILE,
        path = LocalPath.build("data", "misc", "profiles", "cur", "0"),
        userHandle = UserHandle2(0),
    )
    private val areaProfile3 = DataArea(
        type = DataArea.Type.ART_PROFILE,
        path = LocalPath.build("data_mirror", "ref_profiles"),
        userHandle = UserHandle2(-1),
    )
    private val areaProfile4 = DataArea(
        type = DataArea.Type.ART_PROFILE,
        path = LocalPath.build("data_mirror", "cur_profiles", "0"),
        userHandle = UserHandle2(0),
    )

    private val profilePaths = setOf(
        areaProfile1.path,
        areaProfile2.path,
        areaProfile3.path,
        areaProfile4.path,
    )

    @Before override fun setup() {
        super.setup()

        every { areaManager.state } returns flowOf(
            DataAreaManager.State(
                areas = setOf(
                    areaProfile1,
                    areaProfile2,
                    areaProfile3,
                    areaProfile4,
                )
            )
        )
    }

    private fun getProcessor() = ArtProfileCSI(
        areaManager = areaManager,
        dirNameCheck = DirNameCheck(pkgRepo),
    )

    @Test override fun `test jurisdiction`() = runTest {
        getProcessor().assertJurisdiction(DataArea.Type.ART_PROFILE)
    }

    @Test override fun `determine area successfully`() = runTest {
        val processor = getProcessor()

        for (profilePath in profilePaths) {
            val testFile1 = profilePath.child(rngString)
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.ART_PROFILE
                prefix shouldBe profilePath
                prefixFreeSegments shouldBe testFile1.removePrefix(profilePath)
                isBlackListLocation shouldBe true
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()

        processor.identifyArea(LocalPath.build("/data/misc/profile", rngString)) shouldBe null
        processor.identifyArea(LocalPath.build("/data/misc/profile/cur", rngString)) shouldBe null
        processor.identifyArea(LocalPath.build("/data_mirror/ref_profiles")) shouldBe null
        processor.identifyArea(LocalPath.build("/data_mirror/cur_profiles/0")) shouldBe null
    }

    @Test fun `find default owner`() = runTest {
        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        for (base in profilePaths) {
            val toHit = base.child("eu.thedarken.sdm.test")
            val locationInfo = getProcessor().identifyArea(toHit)!!

            getProcessor().findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
                hasKnownUnknownOwner shouldBe false
            }
        }

        for (base in profilePaths) {
            val toHit = base.child("eu.thedarken.sdm.test/abc/def")
            val locationInfo = getProcessor().identifyArea(toHit)!!.apply {
                prefix shouldBe base
            }

            getProcessor().findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
                hasKnownUnknownOwner shouldBe false
            }
        }
    }

    @Test fun `find no owner or fallback`() = runTest {
        val processor = getProcessor()

        for (profile in profilePaths) {
            val testFile1 = profile.child(rngString)
            val areaInfo = processor.identifyArea(testFile1)!!

            processor.findOwners(areaInfo).apply {
                owners shouldBe emptySet()
                hasKnownUnknownOwner shouldBe false
            }
        }
    }
}