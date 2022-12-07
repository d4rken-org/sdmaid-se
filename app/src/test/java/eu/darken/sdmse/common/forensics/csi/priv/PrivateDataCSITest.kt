package eu.darken.sdmse.common.forensics.csi.priv

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.pkgs.container.ApkInfo
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.*

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
    ).map { it as LocalPath }

    @Before override fun setup() {
        super.setup()

        every { areaManager.areas } returns flowOf(
            setOf(
                privData1,
                privData2,
                privData3,
                privData4,
                privData5,
                privData6,
                privData7
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
            val testFile1 = LocalPath.build(base, UUID.randomUUID().toString())
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.PRIVATE_DATA
                prefix shouldBe "${base.path}/"
                prefixFreePath shouldBe testFile1.name
                isBlackListLocation shouldBe true
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()

        processor.identifyArea(LocalPath.build("/data", UUID.randomUUID().toString())) shouldBe null
        processor.identifyArea(LocalPath.build("/mnt/expand", UUID.randomUUID().toString())) shouldBe null
    }

    @Test fun `find default owner`() = runTest {
        val processor = getProcessor()

        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        for (base in bases) {
            val toHit = LocalPath.build(base, "eu.thedarken.sdm.test")
            val locationInfo = processor.identifyArea(toHit)!!.apply {

            }

            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }

        for (base in bases) {
            val toHit = LocalPath.build(base, "eu.thedarken.sdm.test/abc/def")
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

        val prefixFree = UUID.randomUUID().toString()
        mockMarker(packageName, DataArea.Type.PRIVATE_DATA, prefixFree)

        for (base in bases) {
            val toHit = LocalPath.build(base, prefixFree)
            val locationInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe "${base.path}${File.separator}"
            }

            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }
    }

    @Test fun `find no owner or fallback`() = runTest {
        val processor = getProcessor()

        for (base in bases) {
            val locationInfo = processor.identifyArea(LocalPath.build(base, "_test.package/test"))!!

            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe "test.package".toPkgId()
            }
        }

        for (base in bases) {
            val testFile1 = LocalPath.build(base, UUID.randomUUID().toString())
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

    /**
     * https://github.com/d4rken/sdmaid-public/issues/615
     */
    @Test fun testLGEThemeIssue() = runTest {
        val processor = getProcessor()

        val corpseName = "com.lge.theme.highcontrast.browser"
        val packageName = "com.lge.theme.highcontrast".toPkgId()

        for (base in bases) {
            val areaInfo = processor.identifyArea(LocalPath.build(base, corpseName))!!

            processor.findOwners(areaInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }

        mockPkg(packageName)
        for (base in bases) {
            val areaInfo = processor.identifyArea(LocalPath.build(base, corpseName))!!

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
            val areaInfo = processor.identifyArea(LocalPath.build(base, corpseName))!!
            processor.findOwners(areaInfo).apply {
                owners.single().pkgId shouldBe ownerPackage.toPkgId()
            }
        }
        mockPkg(ownerPackage.toPkgId())

        for (base in bases) {
            val areaInfo = processor.identifyArea(LocalPath.build(base, corpseName))!!
            processor.findOwners(areaInfo).apply {
                owners.single().pkgId shouldBe ownerPackage.toPkgId()
            }
        }
    }
}