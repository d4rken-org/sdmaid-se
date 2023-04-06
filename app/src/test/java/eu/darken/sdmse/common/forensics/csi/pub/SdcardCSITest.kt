package eu.darken.sdmse.common.forensics.csi.pub

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.removePrefix
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.rngString
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
class SdcardCSITest : BaseCSITest() {
    val basePathSdcard1 = LocalPath.build("/card1")
    val basePathSdcard2 = LocalPath.build("/card2")
    val sdcards = setOf(basePathSdcard1, basePathSdcard2)

    val storagePrivateData1 = mockk<DataArea>().apply {
        every { path } returns LocalPath.build("/data/data")
        every { type } returns DataArea.Type.PRIVATE_DATA
        every { flags } returns emptySet()
    }

    val storagePrivateData2 = mockk<DataArea>().apply {
        every { path } returns LocalPath.build("/data_mirror/data_de/null/0")
        every { type } returns DataArea.Type.PRIVATE_DATA
        every { flags } returns emptySet()
    }

    val storageSdcard1 = mockk<DataArea>().apply {
        every { path } returns basePathSdcard1
        every { type } returns DataArea.Type.SDCARD
        every { flags } returns setOf(DataArea.Flag.PRIMARY)
    }
    val storagePublicData1 = mockk<DataArea>().apply {
        every { path } returns LocalPath.build(basePathSdcard1, "Android/data")
        every { type } returns DataArea.Type.PUBLIC_DATA
        every { flags } returns emptySet()
    }
    val storagePublicObb1 = mockk<DataArea>().apply {
        every { path } returns LocalPath.build(basePathSdcard1, "Android/obb")
        every { type } returns DataArea.Type.PUBLIC_OBB
        every { flags } returns emptySet()
    }
    val storagePublicMedia1 = mockk<DataArea>().apply {
        every { path } returns LocalPath.build(basePathSdcard1, "Android/media")
        every { type } returns DataArea.Type.PUBLIC_MEDIA
        every { flags } returns emptySet()
    }

    val storageSdcard2 = mockk<DataArea>().apply {
        every { path } returns basePathSdcard2
        every { type } returns DataArea.Type.SDCARD
        every { flags } returns emptySet()
    }
    val storagePublicData2 = mockk<DataArea>().apply {
        every { path } returns LocalPath.build(basePathSdcard2, "Android/data")
        every { type } returns DataArea.Type.DATA
        every { flags } returns emptySet()
    }
    val storagePublicObb2 = mockk<DataArea>().apply {
        every { path } returns LocalPath.build(basePathSdcard2, "Android/obb")
        every { type } returns DataArea.Type.PUBLIC_OBB
        every { flags } returns emptySet()
    }
    val storagePublicMedia2 = mockk<DataArea>().apply {
        every { path } returns LocalPath.build(basePathSdcard2, "Android/media")
        every { type } returns DataArea.Type.PUBLIC_MEDIA
        every { flags } returns emptySet()
    }

    @Before override fun setup() {
        MockKAnnotations.init(this)
        super.setup()

        every { areaManager.state } returns flowOf(
            DataAreaManager.State(
                areas = setOf(
                    storagePrivateData1,
                    storagePrivateData2,
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

    private fun getProcessor() = SdcardCSI(
        areaManager = areaManager,
        clutterRepo = clutterRepo,
    )

    @Test override fun `test jurisdiction`() = runTest {
        getProcessor().assertJurisdiction(DataArea.Type.SDCARD)
    }

    @Test override fun `determine area successfully`() = runTest {
        val processor = getProcessor()
        for (base in sdcards) {
            val testFile1 = base.child(rngString)
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.SDCARD
                prefixFreePath shouldBe testFile1.removePrefix(base)
                prefix shouldBe base
                isBlackListLocation shouldBe false
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()
        for (base in sdcards) {
            processor.identifyArea(base.child("Android/data", rngString)) shouldBe null
            processor.identifyArea(base.child("Android/media", rngString)) shouldBe null
            processor.identifyArea(base.child("Android/obb", rngString)) shouldBe null
        }

        processor.identifyArea(
            LocalPath.build("/some/folder")
        ) shouldBe null
    }

    @Test fun `find owner via direct clutter hit`() = runTest {
        val processor = getProcessor()
        val pkgId = "com.test.pkg".toPkgId()
        mockPkg(pkgId, null)

        val prefixFree = rngString
        mockMarker(pkgId, DataArea.Type.SDCARD, prefixFree)

        for (base in sdcards) {
            val toHit = base.child(prefixFree)

            val areaInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe base
            }

            processor.findOwners(areaInfo).apply {
                owners.single().pkgId shouldBe pkgId
            }
        }
    }

    @Test fun `find owner via nested clutter hit`() = runTest {
        val processor = getProcessor()
        val pkgId = "com.test.pkg".toPkgId()
        mockPkg(pkgId, null)

        val prefixFree = rngString
        mockMarker(pkgId, DataArea.Type.SDCARD, prefixFree)

        for (base in sdcards) {
            val toHit = base.child("$prefixFree/${UUID.randomUUID()}")

            val areaInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe base
            }

            processor.findOwners(areaInfo).apply {
                owners.single().pkgId shouldBe pkgId
            }
        }
    }

    @Test fun `find owner via nested clutter hit with an uninstalled subowner`() = runTest {
        val processor = getProcessor()

        val packageName1 = "com.test.pkg".toPkgId()
        mockPkg(packageName1)

        val prefixFree1 = "sdm_test_path_default_dir"
        mockMarker(packageName1, DataArea.Type.SDCARD, prefixFree1)

        val packageName2 = "com.test.pkg2".toPkgId()
        val prefixFree2 = "$prefixFree1/sdm_test_path_nested_dir"
        mockMarker(packageName2, DataArea.Type.SDCARD, prefixFree2)

        for (base in sdcards) {
            val toHit = base.child(prefixFree2)
            val areaInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe base
            }

            processor.findOwners(areaInfo).apply {
                owners.single().pkgId shouldBe packageName2
            }
        }
    }

    @Test fun `nested owners child is installed, parent is not`() = runTest {
        val processor = getProcessor()

        val packageName1 = "com.test.pkg1".toPkgId()
        val prefixFree1 = "sdm_test_path_default_dir"
        mockMarker(packageName1, DataArea.Type.SDCARD, prefixFree1)

        val packageName2 = "com.test.pkg2".toPkgId()
        mockPkg(packageName2)

        val prefixFree2 = "$prefixFree1/sdm_test_path_nested_dir"
        mockMarker(packageName2, DataArea.Type.SDCARD, prefixFree2)

        for (base in sdcards) {
            val toHit = base.child(prefixFree1)
            val areaInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe base
            }

            processor.findOwners(areaInfo).apply {
                owners.single().pkgId shouldBe packageName1
            }
        }
    }

    @Test fun `tripple nesting`() = runTest {
        val processor = getProcessor()

        val packageName1 = "com.test.pkg1".toPkgId()
        val prefixFree1 = "sdm_test_blocked_corpse"
        mockMarker(packageName1, DataArea.Type.SDCARD, prefixFree1)

        val packageName2 = "com.test.pkg2".toPkgId()
        mockPkg(packageName2)
        val prefixFree2 = "$prefixFree1/sdm_test_blocking_child"
        mockMarker(packageName2, DataArea.Type.SDCARD, prefixFree2)

        val packageName3 = "com.test.pkg3".toPkgId()
        val prefixFree3 = "$prefixFree2/sdm_test_cascaded_corpse"
        mockMarker(packageName3, DataArea.Type.SDCARD, prefixFree3)

        for (base in sdcards) {
            val toHit = base.child(prefixFree3)
            val locationInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe base
            }
            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName3
            }
        }
        for (base in sdcards) {
            val toHit = base.child(prefixFree2)
            val locationInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe base
            }

            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName2
            }
        }
    }

    @Test fun `find no owner or fallback`() = runTest {
        val processor = getProcessor()

        for (base in sdcards) {
            val testFile1 = base.child(rngString)
            val locationInfo1 = processor.identifyArea(testFile1)!!

            processor.findOwners(locationInfo1).apply {
                owners.size shouldBe 0
                hasKnownUnknownOwner shouldBe false
            }
        }
    }

}