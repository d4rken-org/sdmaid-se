package eu.darken.sdmse.common.forensics.csi.priv

import android.content.pm.ApplicationInfo
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.removePrefix
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.container.NormalPkg
import eu.darken.sdmse.common.pkgs.container.PkgArchive
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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

    @BeforeEach override fun setup() {
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

        // Default: ownership lookup yields no uid, so UID attribution is a no-op unless a test opts in
        stubOwnerUid(null)
    }

    private fun stubOwnerUid(uid: Int?) {
        coEvery { gatewaySwitch.lookupExtended(any()) } returns mockk {
            every { ownership } returns uid?.let { value -> mockk { every { userId } returns value.toLong() } }
        }
    }

    private fun mockNormalPkg(
        pkgId: Pkg.Id,
        uid: Int,
        userHandle: UserHandle2 = UserHandle2(0),
    ): NormalPkg = mockk<NormalPkg>().apply {
        every { id } returns pkgId
        every { this@apply.userHandle } returns userHandle
        every { applicationInfo } returns mockk<ApplicationInfo>(relaxed = true).apply { this.uid = uid }
    }

    private fun getProcessor() = PrivateDataCSI(
        areaManager = areaManager,
        pkgRepo = pkgRepo,
        clutterRepo = clutterRepo,
        userManager = userManager2,
        storageEnvironment = storageEnvironment,
        pkgOps = pkgOps,
        gatewaySwitch = gatewaySwitch,
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

    @Test fun `uid maps to a single installed package - attributed as owner`() = runTest {
        val processor = getProcessor()

        val wifiAi = "com.samsung.android.wifi.ai".toPkgId()
        every { pkgRepo.data } returns flowOf(PkgRepo.PkgData.from(setOf(mockNormalPkg(wifiAi, uid = 1000))))
        stubOwnerUid(1000)

        // Directory named after an uninstalled package, but its data is owned by an installed package's uid
        val toHit = privData1.path.child("com.samsung.android.wifi.intelligence")
        val locationInfo = processor.identifyArea(toHit)!!

        processor.findOwners(locationInfo).apply {
            owners.single().pkgId shouldBe wifiAi
            hasKnownUnknownOwner shouldBe false
        }
    }

    @Test fun `uid shared by multiple installed packages - reported as known unknown owner`() = runTest {
        val processor = getProcessor()

        every { pkgRepo.data } returns flowOf(
            PkgRepo.PkgData.from(
                setOf(
                    mockNormalPkg("android".toPkgId(), uid = 1000),
                    mockNormalPkg("com.samsung.android.wifi.ai".toPkgId(), uid = 1000),
                )
            )
        )
        stubOwnerUid(1000)

        val toHit = privData1.path.child("com.samsung.android.wifi.intelligence")
        val locationInfo = processor.identifyArea(toHit)!!

        processor.findOwners(locationInfo).apply {
            owners.isEmpty() shouldBe true
            hasKnownUnknownOwner shouldBe true
        }
    }

    @Test fun `uid resolves to no installed package - still a corpse via fallback`() = runTest {
        val processor = getProcessor()

        every { pkgRepo.data } returns flowOf(PkgRepo.PkgData.from(emptySet()))
        stubOwnerUid(12345)

        val toHit = privData1.path.child("com.gone.app")
        val locationInfo = processor.identifyArea(toHit)!!

        processor.findOwners(locationInfo).apply {
            owners.single().pkgId shouldBe "com.gone.app".toPkgId()
            hasKnownUnknownOwner shouldBe false
        }
    }

    @Test fun `ownership lookup failure falls through to fallback`() = runTest {
        val processor = getProcessor()

        coEvery { gatewaySwitch.lookupExtended(any()) } throws IllegalStateException("boom")

        val toHit = privData1.path.child("com.gone.app")
        val locationInfo = processor.identifyArea(toHit)!!

        processor.findOwners(locationInfo).apply {
            owners.single().pkgId shouldBe "com.gone.app".toPkgId()
            hasKnownUnknownOwner shouldBe false
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
        } returns PkgArchive(
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