package eu.darken.sdmse.common.forensics.csi.pubs

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.*

class CSIPublicMediaTest : BaseCSITest() {
    val sdcardPath1 = LocalPath.build("/card1")
    val sdcardPath2 = LocalPath.build("/card2")

    val storageSdcard1 = mockk<DataArea>().apply {
        every { path } returns sdcardPath1
        every { type } returns DataArea.Type.SDCARD
        every { flags } returns emptySet()
    }
    val storagePublicData1 = mockk<DataArea>().apply {
        every { path } returns LocalPath.build(sdcardPath1, "Android/data")
        every { type } returns DataArea.Type.PUBLIC_DATA
        every { flags } returns emptySet()
    }
    val storagePublicObb1 = mockk<DataArea>().apply {
        every { path } returns LocalPath.build(sdcardPath1, "Android/obb")
        every { type } returns DataArea.Type.PUBLIC_OBB
        every { flags } returns emptySet()
    }
    val storagePublicMedia1 = mockk<DataArea>().apply {
        every { path } returns LocalPath.build(sdcardPath1, "Android/media")
        every { type } returns DataArea.Type.PUBLIC_MEDIA
        every { flags } returns emptySet()
    }

    val storageSdcard2 = mockk<DataArea>().apply {
        every { path } returns sdcardPath2
        every { type } returns DataArea.Type.SDCARD
        every { flags } returns emptySet()
    }
    val storagePublicData2 = mockk<DataArea>().apply {
        every { path } returns LocalPath.build(sdcardPath2, "Android/data")
        every { type } returns DataArea.Type.PUBLIC_DATA
        every { flags } returns emptySet()
    }
    val storagePublicObb2 = mockk<DataArea>().apply {
        every { path } returns LocalPath.build(sdcardPath2, "Android/obb")
        every { type } returns DataArea.Type.PUBLIC_OBB
        every { flags } returns emptySet()
    }
    val storagePublicMedia2 = mockk<DataArea>().apply {
        every { path } returns LocalPath.build(sdcardPath2, "Android/media")
        every { type } returns DataArea.Type.PUBLIC_MEDIA
        every { flags } returns emptySet()
    }

    val mediaPaths = setOf(
        storagePublicMedia1.path as LocalPath,
        storagePublicMedia2.path as LocalPath
    )

    @Before override fun setup() {
        MockKAnnotations.init(this)
        super.setup()

        every { areaManager.areas } returns flowOf(
            setOf(
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
    }

    private fun getProcessor() = CSIPublicMedia(
        areaManager = areaManager,
        pkgRepo = pkgRepo,
        clutterRepo = clutterRepo,
    )

    override fun `test jurisdiction`() = runTest {
        getProcessor().assertJurisdiction(DataArea.Type.PUBLIC_MEDIA)
    }

    override fun `determine area successfully`() = runTest {
        val processor = getProcessor()
        for (base in mediaPaths) {
            val testFile1 = LocalPath.build(base, UUID.randomUUID().toString())
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.PUBLIC_MEDIA
                prefixFreePath shouldBe testFile1.name
                prefix shouldBe "${base.path}/"
                isBlackListLocation shouldBe true
            }
        }
    }

    override fun `determine area UNsuccessfully`() = runTest {
        val processor = getProcessor()
        for (base in mediaPaths) {
            processor.identifyArea(LocalPath.build("$base/Android/data", UUID.randomUUID().toString())) shouldBe null
            processor.identifyArea(LocalPath.build("$base/Android/obb", UUID.randomUUID().toString())) shouldBe null
            processor.identifyArea(LocalPath.build("$base/Android", UUID.randomUUID().toString())) shouldBe null
        }
    }

    override fun `find default owner`() = runTest {
        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockApp(packageName)

        for (base in mediaPaths) {
            val toHit = LocalPath.build(base, "eu.thedarken.sdm.test")
            val locationInfo = getProcessor().identifyArea(toHit)!!

            getProcessor().findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
                hasKnownUnknownOwner shouldBe false
            }
        }

        for (base in mediaPaths) {
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

    override fun `find default owner indirectly`() = runTest {

    }

    override fun `find owner via direct clutter hit`() = runTest {
        val processor = getProcessor()
        val pkgId = "com.test.pkg".toPkgId()
        mockApp(pkgId, null)

        val prefixFree = UUID.randomUUID().toString()
        mockMarker(pkgId, DataArea.Type.PUBLIC_MEDIA, prefixFree)

        for (base in mediaPaths) {
            val toHit = LocalPath.build(base, prefixFree)

            val areaInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe "${base.path}/"
            }

            processor.findOwners(areaInfo).apply {
                owners.single().pkgId shouldBe pkgId
            }
        }
    }

    override fun `find no owner or fallback`() = runTest {
        val processor = getProcessor()

        for (base in mediaPaths) {
            val testFile1 = LocalPath.build(base, UUID.randomUUID().toString())
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
        mockApp(packageName)

        for (base in mediaPaths) {
            val targets = listOf(
                LocalPath.build(base, "_eu.thedarken.sdm.test/test"),
                LocalPath.build(base, ".eu.thedarken.sdm.test/test"),
                LocalPath.build(base, ".external.eu.thedarken.sdm.test/test")
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

        for (base in mediaPaths) {
            val locationInfo = processor.identifyArea(LocalPath.build(base, "_test.package/test"))!!

            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe "test.package".toPkgId()
            }
        }
    }
}