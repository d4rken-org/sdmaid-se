package eu.darken.sdmse.common.forensics.csi.source

import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.removePrefix
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.forensics.csi.source.tools.*
import eu.darken.sdmse.common.pkgs.container.ApkInfo
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AppSourceMainCSITest : BaseCSITest() {


    private val appSourcesArea = DataArea(
        type = DataArea.Type.APP_APP,
        path = LocalPath.build("/data/app"),
        userHandle = UserHandle2(-1),
    )

    private val bases = setOf(
        appSourcesArea.path,
    )

    @Before override fun setup() {
        super.setup()

        mockkObject(BuildWrap)
        every { BuildWrap.FINGERPRINT } returns ""
        every { BuildWrap.VERSION } returns mockk<BuildWrap.VersionWrap>().apply {
            every { SDK_INT } returns 30
        }

        every { areaManager.state } returns flowOf(
            DataAreaManager.State(
                areas = setOf(
                    appSourcesArea,
                )
            )
        )
    }

    private fun getProcessor() = AppSourceMainCSI(
        areaManager = areaManager,
        similarityFilter = SimilarityFilter(pkgRepo),
        sourceChecks = setOf(
            ApkDirCheck(pkgOps),
            AppSourceClutterCheck(clutterRepo),
            DirectApkCheck(pkgOps),
            DirToPkgCheck(pkgRepo),
            FileToPkgCheck(pkgRepo),
            LuckyPatcherCheck(pkgRepo),
            SubDirToPkgCheck(gatewaySwitch),
        )
    )

    override fun `test jurisdiction`() = runTest {
        getProcessor().assertJurisdiction(DataArea.Type.APP_APP)
    }

    @Test override fun `determine area successfully`() = runTest {
        val processor = getProcessor()

        for (base in bases) {
            val testFile1 = base.child(rngString)
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.APP_APP
                prefix shouldBe base
                prefixFreePath shouldBe testFile1.removePrefix(base)
                isBlackListLocation shouldBe true
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()

        processor.identifyArea(LocalPath.build("/data", rngString)) shouldBe null
        processor.identifyArea(LocalPath.build("/data/app-private", rngString)) shouldBe null
        processor.identifyArea(LocalPath.build("/data/data", rngString)) shouldBe null
    }

    @Test fun testProcess_hit() = runTest {
        val processor = getProcessor()

        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        val targets = bases.map {
            setOf(
                it.child("eu.thedarken.sdm.test-1.apk"),
                it.child("eu.thedarken.sdm.test-12.apk"),
                it.child("eu.thedarken.sdm.test-123.apk"),
                it.child("eu.thedarken.sdm.test-1"),
                it.child("eu.thedarken.sdm.test-12"),
                it.child("eu.thedarken.sdm.test-123"),
                it.child("eu.thedarken.sdm.test-RLEuLDrRIaICTBfF4FhaFg==/base.apk"),
            )
        }.flatten()

        for (toHit in targets) {
            val locationInfo = processor.identifyArea(toHit)!!
            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }
    }

    @Test fun testProcess_hit_child() = runTest {
        val processor = getProcessor()

        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        val targets = bases.map {
            setOf(
                it.child("eu.thedarken.sdm.test-123/abc"),
                it.child("eu.thedarken.sdm.test-123/abc/def"),
                it.child("eu.thedarken.sdm.test-RLEuLDrRIaICTBfF4FhaFg==/abc/def"),
            )
        }.flatten()

        for (toHit in targets) {
            val locationInfo = processor.identifyArea(toHit)!!
            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }
    }

    @Test fun testProcess_hit_archiveinfo() = runTest {
        val processor = getProcessor()

        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        val targets = bases.map {
            setOf(
                it.child("test.apk"),
            )
        }.flatten()

        val apkArchive = mockk<ApkInfo>().apply {
            every { id } returns packageName
        }
        for (toHit in targets) {
            coEvery { pkgOps.viewArchive(toHit, 0) } returns apkArchive

            val locationInfo = processor.identifyArea(toHit)!!
            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }
    }

    @Test fun testProcess_hit_nested_archiveinfo() = runTest {
        val processor = getProcessor()

        val packageName = "some.pkg".toPkgId()
        mockPkg(packageName)

        val targets = bases.map {
            setOf(
                it.child("ApiDemos"),
            )
        }.flatten()

        val validApkNames = listOf(
            "ApiDemos.apk",
            "base.apk"
        )

        for (base in targets) {
            for (apkName in validApkNames) {
                val target = base.child(apkName)
                val apkArchive = mockk<ApkInfo>().apply {
                    every { id } returns packageName
                }
                coEvery { pkgOps.viewArchive(target, 0) } returns apkArchive

                val locationInfo = processor.identifyArea(target)!!
                processor.findOwners(locationInfo).apply {
                    owners.single().pkgId shouldBe packageName
                }
            }
        }
    }

    @Test fun testProcess_clutter_hit() = runTest {
        val processor = getProcessor()

        val packageName = "some.pkg".toPkgId()

        val prefixFree = rngString
        mockMarker(packageName, DataArea.Type.APP_APP, prefixFree)

        for (base in bases) {

            val locationInfo = processor.identifyArea(base.child(prefixFree))!!
            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }
    }

    @Test fun testProcess_oddones() = runTest {
        val processor = getProcessor()

        val packageName = "com.forpda.lp".toPkgId()
        mockPkg(packageName)

        val prefixFree = "something-123.odex"

        for (base in bases) {
            val toHit = base.child(prefixFree)
            val locationInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe base
                prefixFreePath shouldBe listOf(prefixFree)
            }

            processor.findOwners(locationInfo).apply {
                owners.single().apply {
                    pkgId shouldBe packageName
                    flags shouldBe setOf(Marker.Flag.CUSTODIAN)
                }
            }
        }
    }

    @Test fun testProcess_nothing() = runTest {
        val processor = getProcessor()

        for (base in bases) {
            val suffix = rngString
            val toHit = base.child(suffix)
            val locationInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe base
                prefixFreePath shouldBe listOf(suffix)
            }

            processor.findOwners(locationInfo).apply {
                owners.size shouldBe 0
                hasKnownUnknownOwner shouldBe false
            }
        }
    }

    @Test fun testStrictMatching_no_false_positive_dir() = runTest {
        val processor = getProcessor()

        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName, LocalPath.build("/data/app", "eu.thedarken.sdm.test-2/base.apk"))

        for (base in bases) {
            run {
                // Stale and shouldn't have an owner
                val toHit = base.child("eu.thedarken.sdm.test-1")
                val locationInfo = processor.identifyArea(toHit)!!

                processor.findOwners(locationInfo).apply {
                    owners.size shouldBe 0
                    hasKnownUnknownOwner shouldBe false
                }
            }
            run {
                // Stale and shouldn't have an owner
                val toHit = base.child("eu.thedarken.sdm.test-1/base.apk")
                val locationInfo = processor.identifyArea(toHit)!!

                processor.findOwners(locationInfo).apply {
                    owners.size shouldBe 0
                    hasKnownUnknownOwner shouldBe false
                }
            }
            run {
                val toHit = base.child("eu.thedarken.sdm.test-2")
                val locationInfo = processor.identifyArea(toHit)!!
                processor.findOwners(locationInfo).apply {
                    owners.single().pkgId shouldBe packageName
                }
            }
            run {
                val toHit = base.child("eu.thedarken.sdm.test-2/base.apk")
                val locationInfo = processor.identifyArea(toHit)!!
                processor.findOwners(locationInfo).apply {
                    owners.single().pkgId shouldBe packageName
                }
            }
        }
    }

    @Test fun testStrictMatching_no_false_positive_file() = runTest {
        val processor = getProcessor()
        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName, LocalPath.build("/data/app", "eu.thedarken.sdm.test-2.apk"))

        for (base in bases) {
            run {
                // Stale and shouldn't have an owner
                val toHit = base.child("eu.thedarken.sdm.test-1")
                val locationInfo = processor.identifyArea(toHit)!!

                processor.findOwners(locationInfo).apply {
                    owners.size shouldBe 0
                    hasKnownUnknownOwner shouldBe false
                }
            }
            run {
                // Stale and shouldn't have an owner
                val toHit = base.child("eu.thedarken.sdm.test-1/base.apk")
                val locationInfo = processor.identifyArea(toHit)!!

                processor.findOwners(locationInfo).apply {
                    owners.size shouldBe 0
                    hasKnownUnknownOwner shouldBe false
                }
            }
            run {
                val toHit = base.child("eu.thedarken.sdm.test-2")
                val locationInfo = processor.identifyArea(toHit)!!
                processor.findOwners(locationInfo).apply {
                    owners.single().pkgId shouldBe packageName
                }
            }
            run {
                val toHit = base.child("eu.thedarken.sdm.test-2/base.apk")
                val locationInfo = processor.identifyArea(toHit)!!
                processor.findOwners(locationInfo).apply {
                    owners.single().pkgId shouldBe packageName
                }
            }
        }
    }
}