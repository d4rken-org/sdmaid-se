package eu.darken.sdmse.common.forensics.csi.priv

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.removePrefix
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.pkgs.container.ApkInfo
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

class PrivateDataCSITest : BaseCSITest() {

    private val privData1 = mockk<DataArea>().apply {
        every { type } returns DataArea.Type.PRIVATE_DATA
        every { flags } returns emptySet()
        every { path } returns LocalPath.build("/data/data")
        every { userHandle } returns UserHandle2(0)
    }
    private val privData2 = mockk<DataArea>().apply {
        every { type } returns DataArea.Type.PRIVATE_DATA
        every { flags } returns emptySet()
        every { path } returns LocalPath.build("/data/user/0")
        every { userHandle } returns UserHandle2(0)
    }
    private val privData3 = mockk<DataArea>().apply {
        every { type } returns DataArea.Type.PRIVATE_DATA
        every { flags } returns emptySet()
        every { path } returns LocalPath.build("/data/user/1")
        every { userHandle } returns UserHandle2(1)
    }
    private val privData4 = mockk<DataArea>().apply {
        every { type } returns DataArea.Type.PRIVATE_DATA
        every { flags } returns emptySet()
        every { path } returns LocalPath.build("/data/user/12")
        every { userHandle } returns UserHandle2(12)
    }
    private val privData5 = mockk<DataArea>().apply {
        every { type } returns DataArea.Type.PRIVATE_DATA
        every { flags } returns emptySet()
        every { path } returns LocalPath.build("/mnt/expand/${UUID.randomUUID()}/user/0")
        every { userHandle } returns UserHandle2(0)
    }
    private val privData6 = mockk<DataArea>().apply {
        every { type } returns DataArea.Type.PRIVATE_DATA
        every { flags } returns emptySet()
        every { path } returns LocalPath.build("/mnt/expand/${UUID.randomUUID()}/user/1")
        every { userHandle } returns UserHandle2(1)
    }
    private val privData7 = mockk<DataArea>().apply {
        every { type } returns DataArea.Type.PRIVATE_DATA
        every { flags } returns emptySet()
        every { path } returns LocalPath.build("/mnt/expand/${UUID.randomUUID()}/user/12")
        every { userHandle } returns UserHandle2(12)
    }

    private val bases = setOf(
        privData1.path,
        privData2.path,
        privData3.path,
        privData4.path,
        privData5.path,
        privData6.path,
        privData7.path,
    )

    @Before override fun setup() {
        super.setup()

        every { areaManager.state } returns flowOf(
            DataAreaManager.State(
                areas = setOf(
                    privData1,
                    privData2,
                    privData3,
                    privData4,
                    privData5,
                    privData6,
                    privData7
                )
            )
        )
    }

    private fun getProcessor() = PrivateDataCSI(
        areaManager = areaManager,
        pkgRepo = pkgRepo,
        clutterRepo = clutterRepo,
        userManager = userManager2,
        storageEnvironment = storageEnvironment,
        pkgOps = pkgOps,
    )

    @Test override fun `test jurisdiction`() = runTest {
        getProcessor().assertJurisdiction(DataArea.Type.PRIVATE_DATA)
    }

    @Test override fun `determine area successfully`() = runTest {
        val processor = getProcessor()

        for (base in bases) {
            val testFile1 = base.child(rngString)
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.PRIVATE_DATA
                prefix shouldBe base
                prefixFreeSegments shouldBe testFile1.removePrefix(base)
                isBlackListLocation shouldBe true
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()

        processor.identifyArea(LocalPath.build("/data", rngString)) shouldBe null
        processor.identifyArea(LocalPath.build("/mnt/expand", rngString)) shouldBe null
    }

    @Test fun `find default owner`() = runTest {
        val processor = getProcessor()

        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        for (base in bases) {
            val toHit = base.child("eu.thedarken.sdm.test")
            val locationInfo = processor.identifyArea(toHit)!!.apply {

            }

            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }

        for (base in bases) {
            val toHit = base.child("eu.thedarken.sdm.test/abc/def")
            val locationInfo = processor.identifyArea(toHit)!!
            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }
    }

    @Test fun `find owner via direct clutter hit`() = runTest {
        val processor = getProcessor()

        val packageName = "com.test.pkg".toPkgId()
        mockPkg(packageName)

        val prefixFree = rngString
        mockMarker(packageName, DataArea.Type.PRIVATE_DATA, prefixFree)

        for (base in bases) {
            val toHit = base.child(prefixFree)
            val locationInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe base
            }

            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }
    }

    @Test fun `find no owner or fallback`() = runTest {
        val processor = getProcessor()

        for (base in bases) {
            val locationInfo = processor.identifyArea(base.child("_test.package/test"))!!

            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe "test.package".toPkgId()
            }
        }

        for (base in bases) {
            val testFile1 = base.child(rngString)
            val locationInfo = processor.identifyArea(testFile1)!!

            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe testFile1.name.toPkgId()
            }
        }
    }

    @Test fun testProcess_hit_weird_prefixes() = runTest {
        val processor = getProcessor()

        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        for (base in bases) {
            val targets = setOf(
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

    /**
     * https://github.com/d4rken/sdmaid-public/issues/615
     */
    @Test fun testLGEThemeIssue() = runTest {
        val processor = getProcessor()

        val corpseName = "com.lge.theme.highcontrast.browser"
        val packageName = "com.lge.theme.highcontrast".toPkgId()

        for (base in bases) {
            val areaInfo = processor.identifyArea(base.child(corpseName))!!

            processor.findOwners(areaInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }

        mockPkg(packageName)
        for (base in bases) {
            val areaInfo = processor.identifyArea(base.child(corpseName))!!

            processor.findOwners(areaInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }
    }

    /**
     * https://github.com/d4rken/sdmaid-public/issues/615
     */
    @Test fun testLGEOverlayIssue() = runTest {
        val processor = getProcessor()

        val corpseName = "com.lge.clock.overlay"
        val ownerPackage = "com.lge.clock"

        coEvery {
            pkgOps.viewArchive(
                path = LocalPath.build("system", "vendor", "overlay", ownerPackage, "$ownerPackage.apk"),
                flags = 0
            )
        } returns ApkInfo(
            id = corpseName.toPkgId(),
            packageInfo = mockk(),
        )

        for (base in bases) {
            val areaInfo = processor.identifyArea(base.child(corpseName))!!
            processor.findOwners(areaInfo).apply {
                owners.single().pkgId shouldBe ownerPackage.toPkgId()
            }
        }
        mockPkg(ownerPackage.toPkgId())

        for (base in bases) {
            val areaInfo = processor.identifyArea(base.child(corpseName))!!
            processor.findOwners(areaInfo).apply {
                owners.single().pkgId shouldBe ownerPackage.toPkgId()
            }
        }
    }
}