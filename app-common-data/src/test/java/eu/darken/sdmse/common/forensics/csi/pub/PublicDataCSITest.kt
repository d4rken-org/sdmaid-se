package eu.darken.sdmse.common.forensics.csi.pub

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.removePrefix
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.every
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class PublicDataCSITest : BaseCSITest() {
    val sdcardPath1 = LocalPath.build("/card1")
    val sdcardPath2 = LocalPath.build("/card2")

    val storageSdcard1 = DataArea(
        path = sdcardPath1,
        type = DataArea.Type.SDCARD,
        userHandle = UserHandle2(0),
    )
    val storagePublicData1 = DataArea(
        path = LocalPath.build(sdcardPath1, "Android/data"),
        type = DataArea.Type.PUBLIC_DATA,
        userHandle = storageSdcard1.userHandle,
    )
    val storagePublicObb1 = DataArea(
        path = LocalPath.build(sdcardPath1, "Android/obb"),
        type = DataArea.Type.PUBLIC_OBB,
        userHandle = UserHandle2(0),
    )
    val storagePublicMedia1 = DataArea(
        path = LocalPath.build(sdcardPath1, "Android/media"),
        type = DataArea.Type.PUBLIC_MEDIA,
        userHandle = UserHandle2(0),
    )

    val storageSdcard2 = DataArea(
        path = sdcardPath2,
        type = DataArea.Type.SDCARD,
        userHandle = UserHandle2(10),
    )
    val storagePublicData2 = DataArea(
        path = LocalPath.build(sdcardPath2, "Android/data"),
        type = DataArea.Type.PUBLIC_DATA,
        userHandle = storageSdcard2.userHandle
    )
    val storagePublicObb2 = DataArea(
        path = LocalPath.build(sdcardPath2, "Android/obb"),
        type = DataArea.Type.PUBLIC_OBB,
        userHandle = storageSdcard2.userHandle
    )
    val storagePublicMedia2 = DataArea(
        path = LocalPath.build(sdcardPath2, "Android/media"),
        type = DataArea.Type.PUBLIC_MEDIA,
        userHandle = storageSdcard2.userHandle
    )
    val sdcardPaths = setOf(
        storageSdcard1.path,
        storageSdcard2.path
    )
    val dataPaths = setOf(
        storagePublicData1.path,
        storagePublicData2.path
    )

    @Before override fun setup() {
        MockKAnnotations.init(this)
        super.setup()

        every { areaManager.state } returns flowOf(
            DataAreaManager.State(
                areas = setOf(
                    storageSdcard1,
                    storagePublicData1,
                    storagePublicObb1,
                    storagePublicMedia1,
                    storageSdcard2,
                    storagePublicData2,
                    storagePublicObb2,
                    storagePublicMedia2,
                )
            )
        )
    }

    private fun getProcessor() = PublicDataCSI(
        areaManager = areaManager,
        pkgRepo = pkgRepo,
        clutterRepo = clutterRepo,
    )

    @Test override fun `test jurisdiction`() = runTest {
        getProcessor().assertJurisdiction(DataArea.Type.PUBLIC_DATA)
    }

    @Test override fun `determine area successfully`() = runTest {
        val processor = getProcessor()
        for (base in dataPaths) {
            val testFile1 = base.child(rngString)
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.PUBLIC_DATA
                prefixFreeSegments shouldBe testFile1.removePrefix(base)
                prefix shouldBe base
                isBlackListLocation shouldBe true
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()
        for (base in sdcardPaths) {
            processor.identifyArea(base.child("Android/obb", rngString)) shouldBe null
            processor.identifyArea(base.child("Android/media", rngString)) shouldBe null
            processor.identifyArea(base.child("Android", rngString)) shouldBe null
        }
    }

    @Test fun `find default owner`() = runTest {
        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        for (base in dataPaths) {
            val toHit = base.child("eu.thedarken.sdm.test")
            val locationInfo = getProcessor().identifyArea(toHit)!!

            getProcessor().findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
                hasKnownUnknownOwner shouldBe false
            }
        }

        for (base in dataPaths) {
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

    @Test fun `find owner via direct clutter hit`() = runTest {
        val processor = getProcessor()
        val pkgId = "com.test.pkg".toPkgId()
        mockPkg(pkgId, null)

        val prefixFree = rngString
        mockMarker(pkgId, DataArea.Type.PUBLIC_DATA, prefixFree)

        for (base in dataPaths) {
            val toHit = base.child(prefixFree)

            val areaInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe base
            }

            processor.findOwners(areaInfo).apply {
                owners.single().pkgId shouldBe pkgId
            }
        }
    }

    @Test fun `find no owner or fallback`() = runTest {
        val processor = getProcessor()

        for (base in dataPaths) {
            val testFile1 = base.child(rngString)
            val locationInfo1 = processor.identifyArea(testFile1)!!

            processor.findOwners(locationInfo1).apply {
                owners.single().pkgId shouldBe testFile1.name.toPkgId()
                hasKnownUnknownOwner shouldBe false
            }
        }
    }


    @Test fun testProcess_hit_weird_prefixes() = runTest {
        val processor = getProcessor()

        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        for (base in dataPaths) {
            val targets = listOf(
                base.child("_eu.thedarken.sdm.test/test"),
                base.child(".eu.thedarken.sdm.test/test"),
                base.child(".external.eu.thedarken.sdm.test/test")
            )
            for (toHit in targets) {
                val locationInfo = processor.identifyArea(toHit)!!

                processor.findOwners(locationInfo).apply {
                    owners.single().pkgId shouldBe packageName
                }
            }
        }
    }

    @Test fun testFallback() = runTest {
        val processor = getProcessor()

        for (base in dataPaths) {
            val locationInfo = processor.identifyArea(base.child("_test.package/test"))!!

            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe "test.package".toPkgId()
            }
        }
    }

}