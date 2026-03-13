package eu.darken.sdmse.common.forensics.csi.source

import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.files.child
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.removePrefix
import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.forensics.csi.source.tools.ApkDirCheck
import eu.darken.sdmse.common.forensics.csi.source.tools.AppSourceClutterCheck
import eu.darken.sdmse.common.forensics.csi.source.tools.DirToPkgCheck
import eu.darken.sdmse.common.forensics.csi.source.tools.DirectApkCheck
import eu.darken.sdmse.common.forensics.csi.source.tools.FileToPkgCheck
import eu.darken.sdmse.common.forensics.csi.source.tools.LuckyPatcherCheck
import eu.darken.sdmse.common.forensics.csi.source.tools.SimilarityFilter
import eu.darken.sdmse.common.forensics.csi.source.tools.SubDirToPkgCheck
import eu.darken.sdmse.common.pkgs.container.PkgArchive
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
                prefixFreeSegments shouldBe testFile1.removePrefix(base)
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

        val apkArchive = mockk<PkgArchive>().apply {
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
                val apkArchive = mockk<PkgArchive>().apply {
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
                prefixFreeSegments shouldBe listOf(prefixFree)
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
                prefixFreeSegments shouldBe listOf(suffix)
            }

            processor.findOwners(locationInfo).apply {
                owners.size shouldBe 0
                hasKnownUnknownOwner shouldBe false
            }
        }
    }

    @Test fun testProcess_android11_double_hash_standard_packages() = runTest {
        val processor = getProcessor()

        val testCases = listOf(
            "com.example.deeplviewer" to "~~4IQxTCyRFPq4S53KU3KhBQ==/com.example.deeplviewer-ekB8USahHaRHG-eHQqgtaA==/",
            "com.unciv.app" to "~~4SQmc6pIMWUKe4duEnDYLA==/com.unciv.app-tedbtxdTjOq9-Zi5XmVZ9A==/",
            "com.mixplorer.addon.tagger" to "~~D8kt0_yGoTzeRfPqpXig_A==/com.mixplorer.addon.tagger-7Mv8xwIcEfyZQxHsN3crBg==/",
            "com.android.chrome" to "~~KV8oafUkVvQFcQ0CrB9Xcg==/com.android.chrome-u8-iRxh1dNuDLvYFLhiLCg==/"
        )

        for ((packageName, pathPattern) in testCases) {
            val pkgId = packageName.toPkgId()
            mockPkg(pkgId)

            for (base in bases) {
                val toHit = base.child(pathPattern.toSegs())
                val locationInfo = processor.identifyArea(toHit)!!
                processor.findOwners(locationInfo).apply {
                    owners.single().pkgId shouldBe pkgId
                }
            }
        }
    }

    @Test fun testProcess_android11_versioned_trichrome_packages() = runTest {
        val processor = getProcessor()

        val testCases = listOf(
            "com.google.android.trichromelibrary_661308832" to "~~LZPmWFF7U8b5p2t4TdZl_g==/com.google.android.trichromelibrary_661308832-Kg7UPJMq-h9KEhvNLQ_MQQ==/",
            "com.google.android.trichromelibrary_725805143" to "~~Wk3_8lFYVcexJSSp02wnBA==/com.google.android.trichromelibrary_725805143-cdAJ89ohmzvBP2SPp0jbFw==/",
            "com.google.android.trichromelibrary_720416833" to "~~hRTU7eqvvwqGI1M_HDkSYw==/com.google.android.trichromelibrary_720416833-SwGRwNc5AwLq6Ex3nV4SRw==/"
        )

        for ((packageName, pathPattern) in testCases) {
            val pkgId = packageName.toPkgId()
            mockPkg(pkgId)

            for (base in bases) {
                val toHit = base.child(pathPattern)
                val locationInfo = processor.identifyArea(toHit)!!
                processor.findOwners(locationInfo).apply {
                    owners.single().pkgId shouldBe pkgId
                }
            }
        }
    }

    @Test fun testProcess_trichrome_library_apk() = runTest {
        val processor = getProcessor()

        // Test cases with different versioned package names
        val testCases = listOf(
            "com.google.android.trichromelibrary_720416833" to "~~hRTU7eqvvwqGI1M_HDkSYw==/com.google.android.trichromelibrary_720416833-SwGRwNc5AwLq6Ex3nV4SRw==/TrichromeLibrary.apk",
            "com.google.android.trichromelibrary" to "com.google.android.trichromelibrary-1234/TrichromeLibrary.apk",
            "com.google.android.trichromelibrary_661308832" to "com.google.android.trichromelibrary_661308832-RLEuLDrRIaICTBfF4FhaFg==/TrichromeLibrary.apk"
        )

        for ((expectedPkgName, path) in testCases) {
            val packageName = expectedPkgName.toPkgId()
            mockPkg(packageName)

            val targets = bases.map { it.child(path) }

            val apkArchive = mockk<PkgArchive>().apply {
                every { id } returns packageName
                every { tryField<String?>(any()) } returns null
                every { applicationInfo } returns null
                every { requestedPermissions } returns emptySet()
            }

            for (toHit in targets) {
                coEvery { pkgOps.viewArchive(toHit, 0) } returns apkArchive
                coEvery { pkgOps.viewArchive(toHit, any()) } returns apkArchive

                val locationInfo = processor.identifyArea(toHit)!!
                processor.findOwners(locationInfo).apply {
                    owners.map { it.pkgId }.distinct().single() shouldBe packageName
                }
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

    @Test fun `test vmdl tmp directories have no owner and are corpses`() = runTest {
        val processor = getProcessor()

        val testCases = listOf(
            "vmdl1156857058.tmp",
            "vmdl1604118467.tmp",
            "vmdl1951435554.tmp"
        )

        for (base in bases) {
            for (vmdlDir in testCases) {
                run {
                    // Test the tmp directory itself
                    val toHit = base.child(vmdlDir)
                    val locationInfo = processor.identifyArea(toHit)!!

                    locationInfo.isBlackListLocation shouldBe true

                    processor.findOwners(locationInfo).apply {
                        owners.size shouldBe 0
                        hasKnownUnknownOwner shouldBe false
                    }
                }
                run {
                    // Test .dm files within the tmp directory
                    val toHit = base.child(vmdlDir, "com.microsoft.rdc.androidx.dm")
                    val locationInfo = processor.identifyArea(toHit)!!

                    locationInfo.isBlackListLocation shouldBe true

                    processor.findOwners(locationInfo).apply {
                        owners.size shouldBe 0
                        hasKnownUnknownOwner shouldBe false
                    }
                }
            }
        }
    }
}