package eu.darken.sdmse.common.forensics.csi.dalvik

import eu.darken.sdmse.common.Architecture
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.removePrefix
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.forensics.csi.dalvik.tools.ApkCheck
import eu.darken.sdmse.common.forensics.csi.dalvik.tools.CustomDexOptCheck
import eu.darken.sdmse.common.forensics.csi.dalvik.tools.DalvikCandidateGenerator
import eu.darken.sdmse.common.forensics.csi.dalvik.tools.DalvikClutterCheck
import eu.darken.sdmse.common.forensics.csi.dalvik.tools.ExistCheck
import eu.darken.sdmse.common.forensics.csi.dalvik.tools.OddOnesCheck
import eu.darken.sdmse.common.forensics.csi.dalvik.tools.RuntimeTool
import eu.darken.sdmse.common.forensics.csi.dalvik.tools.SourceDirCheck
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

class CSIDalvikDexTest : BaseCSITest() {

    private val base1 = LocalPath.build("/data/dalvik-cache")
    private val base2 = LocalPath.build("/cache/dalvik-cache")

    private val dalvik1 = LocalPath.build(base1, "x86")
    private val dalvik2 = LocalPath.build(base1, "x64")
    private val dalvik3 = LocalPath.build(base2, "x86")
    private val dalvik4 = LocalPath.build(base2, "x64")
    private var dalvikCachesBases = listOf(base1, base2)
    private var dalviks = listOf(dalvik1, dalvik2, dalvik3, dalvik4)
    private var dalviksX86 = listOf(dalvik1, dalvik3)
    private var dalviksX64 = listOf(dalvik2, dalvik4)

    private val storageDalvikProfileX861 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DALVIK_DEX
        every { path } returns dalvik1
        every { userHandle } returns UserHandle2(-1)
    }
    private val storageDalvikProfileX641 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DALVIK_DEX
        every { path } returns dalvik2
        every { userHandle } returns UserHandle2(-1)
    }
    private val storageDalvikProfileX862 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DALVIK_DEX
        every { path } returns dalvik3
        every { userHandle } returns UserHandle2(-1)
    }
    private val storageDalvikProfileX642 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DALVIK_DEX
        every { path } returns dalvik4
        every { userHandle } returns UserHandle2(-1)
    }
    private val storageData1 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_APP
        every { path } returns LocalPath.build("/data/app")
        every { userHandle } returns UserHandle2(-1)
    }
    private val storageData2 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_APP
        every { path } returns LocalPath.build("/mnt/expand/uuid/app")
        every { userHandle } returns UserHandle2(-1)
    }
    private val storageSystem = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.SYSTEM
        every { path } returns LocalPath.build("/system")
        every { userHandle } returns UserHandle2(-1)
    }
    private val storageSystemApp = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.SYSTEM_APP
        every { path } returns LocalPath.build("/system/app")
        every { userHandle } returns UserHandle2(-1)
    }
    private val storageSystemPrivApp = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.SYSTEM_PRIV_APP
        every { path } returns LocalPath.build("/system/priv-app")
        every { userHandle } returns UserHandle2(-1)
    }
    private val storageVendor = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.SYSTEM
        every { path } returns LocalPath.build("/vendor")
        every { userHandle } returns UserHandle2(-1)
    }

    @Before override fun setup() {
        super.setup()
        every { areaManager.state } returns flowOf(
            DataAreaManager.State(
                areas = setOf(
                    storageData1,
                    storageData2,
                    storageSystem,
                    storageSystemApp,
                    storageSystemPrivApp,
                    storageDalvikProfileX861,
                    storageDalvikProfileX641,
                    storageDalvikProfileX862,
                    storageDalvikProfileX642,
                    storageVendor,
                )
            )
        )
    }

    private fun getProcessor() = DalvikDexCSI(
        areaManager = areaManager,
        sourceGenerator = DalvikCandidateGenerator(
            areaManager,
            architecture = mockk<Architecture>().apply {
                every { folderNames } returns listOf("x86", "x64")
            }
        ),
        clutterCheck = DalvikClutterCheck(clutterRepo),
        customDexOptCheck = CustomDexOptCheck(pkgRepo),
        sourceDirCheck = SourceDirCheck(pkgRepo),
        apkCheck = ApkCheck(pkgOps),
        existCheck = ExistCheck(gatewaySwitch),
        oddOnesCheck = OddOnesCheck(
            mockk<RuntimeTool>().apply {
                coEvery { getRuntimeInfo() } returns RuntimeTool.Info(
                    RuntimeTool.Info.Type.ART, "art"
                )
            }
        ),
    )

    @Test override fun `test jurisdiction`() = runTest {
        getProcessor().assertJurisdiction(DataArea.Type.DALVIK_DEX)
    }

    @Test override fun `determine area successfully`() = runTest {
        val processor = getProcessor()

        for (path in dalviks) {
            val testFile1 = LocalPath.build(path, rngString)
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.DALVIK_DEX
                prefix shouldBe path
                prefixFreeSegments shouldBe testFile1.removePrefix(path)
                isBlackListLocation shouldBe true
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()

        processor.identifyArea(LocalPath.build("/data/dalvik-cache", rngString)) shouldBe null
        processor.identifyArea(LocalPath.build("/data/dalvik-cache/arm", rngString)) shouldBe null

        processor.identifyArea(LocalPath.build("/cache/dalvik-cache/profiles/random")) shouldBe null
        processor.identifyArea(LocalPath.build("/data/dalvik-cache/profiles/random")) shouldBe null

        processor.identifyArea(LocalPath.build("/cache/dalvik-cache/something64/random")) shouldBe null
        processor.identifyArea(LocalPath.build("/data/dalvik-cache/something64/random")) shouldBe null
    }


    @Test fun testDetermineLocation_unknown() = runTest {
        val processor = getProcessor()
        for (base in dalvikCachesBases) {
            processor.identifyArea(LocalPath.build(base, rngString)) shouldBe null
            processor.identifyArea(LocalPath.build(base, "/profiles", rngString)) shouldBe null
            processor.identifyArea(LocalPath.build(base, "/something64", rngString)) shouldBe null
        }
    }

    @Test fun testProcess_hit() = runTest {
        val packageName = "com.test.pkg".toPkgId()
        for (base in dalviks) {
            val targets: Collection<Pair<LocalPath, LocalPath>> = listOf(
                Pair(
                    LocalPath.build("/apex/com.android.permission/priv-app/GooglePermissionController@M_2022_06/GooglePermissionController.apk"),
                    LocalPath.build(
                        base,
                        "/data/dalvik-cache/arm64/apex@com.android.permission@priv-app@GooglePermissionController@M_2022_06@GooglePermissionController.apk@classes.vdex"
                    )
                ),
                Pair(
                    LocalPath.build("/apex/com.android.permission/priv-app/GooglePermissionController@M_2022_06/GooglePermissionController.apk"),
                    LocalPath.build(
                        base,
                        "/data/dalvik-cache/arm64/apex@com.android.permission@priv-app@GooglePermissionController@M_2022_06@GooglePermissionController.apk@classes.art"
                    )
                ),
                Pair(
                    LocalPath.build("/data/app/com.test.pkg-1.apk"),
                    LocalPath.build(base, "data@app@com.test.pkg-1.apk@classes.dex")
                ),
                Pair(
                    LocalPath.build("/data/app/com.test.pkg-12.apk"),
                    LocalPath.build(base, "data@app@com.test.pkg-12.apk@classes.dex")
                ),
                Pair(
                    LocalPath.build("/mnt/expand/uuid/app/com.test.pkg-12.apk"),
                    LocalPath.build(base, "mnt@expand@uuid@app@com.test.pkg-12.apk@classes.dex")
                ),
                Pair(
                    LocalPath.build("/data/app/com.test.pkg-12.apk"),
                    LocalPath.build(base, "data@app@com.test.pkg-12.apk@classes.odex")
                ),
                Pair(
                    LocalPath.build("/data/app/com.test.pkg-12.apk"),
                    LocalPath.build(base, "data@app@com.test.pkg-12.apk@classes.dex.art")
                ),
                Pair(
                    LocalPath.build("/data/app/com.test.pkg-12.apk"),
                    LocalPath.build(base, "data@app@com.test.pkg-12.apk@classes.oat")
                ),
                Pair(
                    LocalPath.build("/data/app/com.test.pkg-12.apk"),
                    LocalPath.build(base, "data@app@com.test.pkg-12.apk.dex")
                ),
                Pair(
                    LocalPath.build("/data/app/com.test.pkg-12.apk"),
                    LocalPath.build(base, "data@app@com.test.pkg-12.apk.odex")
                ),
                Pair(
                    LocalPath.build("/data/app/com.test.pkg-12.apk"),
                    LocalPath.build(base, "data@app@com.test.pkg-12.apk.oat")
                ),
                Pair(
                    LocalPath.build("/data/app/com.test.pkg-12.apk"),
                    LocalPath.build(base, "data@app@com.test.pkg-12.apk.vdex")
                ),
                Pair(
                    LocalPath.build("/system/framework/com.test.pkg-2.apk"),
                    LocalPath.build(base, "system@framework@com.test.pkg-2.jar@classes.odex")
                ),
                Pair(
                    LocalPath.build("/system/app/com.test.pkg-2.jar"),
                    LocalPath.build(base, "system@app@com.test.pkg-2.apk@classes.dex")
                ),
                Pair(
                    LocalPath.build("/system/app/Wallet/Wallet.apk"),
                    LocalPath.build(base, "system@app@Wallet@Wallet.apk@classes.dex")
                ),
                Pair(
                    LocalPath.build("/system/priv-app/NetworkRecommendation/NetworkRecommendation.apk"),
                    LocalPath.build(
                        base,
                        "/system@priv-app@NetworkRecommendation@NetworkRecommendation.apk@classes.dex"
                    )

                ),
                Pair(
                    LocalPath.build("/system/priv-app/NetworkRecommendation/NetworkRecommendation.apk"),
                    LocalPath.build(
                        base,
                        "/system@priv-app@NetworkRecommendation@NetworkRecommendation.apk@classes.vdex"
                    )
                ),
            )

            targets.forEach {
                coEvery { gatewaySwitch.exists(it.first) } returns true
            }

            val processor = getProcessor()

            for (toHit in targets) {
                mockPkg(packageName, toHit.first)
                val areaInfo = processor.identifyArea(toHit.second)!!.apply {
                    type shouldBe DataArea.Type.DALVIK_DEX
                }
                processor.findOwners(areaInfo).apply {
                    owners.first().pkgId shouldBe packageName
                }
            }
        }
    }

    @Test fun testProcess_hit_unknown_owner() = runTest {
        val packageName = "com.test.pkg".toPkgId()
        val targets = mutableListOf<Pair<LocalPath, LocalPath>>()
        for (base in dalviksX86) {
            targets.addAll(
                listOf(
                    Pair(
                        LocalPath.build("/system/framework/x86/boot.art"),
                        LocalPath.build(base, "system@framework@boot.art")
                    ),
                    Pair(
                        LocalPath.build("/system/framework/x86/boot.oat"),
                        LocalPath.build(base, "system@framework@boot.oat")
                    ),
                    Pair(
                        LocalPath.build("/system/framework/x86/boot-framework.art"),
                        LocalPath.build(base, "system@framework@boot-framework.art")
                    ),
                    Pair(
                        LocalPath.build("/system/framework/x86/boot-framework.oat"),
                        LocalPath.build(base, "system@framework@boot-framework.oat")
                    ),
                    Pair(
                        LocalPath.build("/system/framework/x86/boot-framework.vdex"),
                        LocalPath.build(base, "system@framework@boot-framework.vdex")
                    ),
                    Pair(
                        LocalPath.build("/system/framework/serviceitems.jar"),
                        LocalPath.build(base, "system@framework@serviceitems.jar@classes.dex")
                    ),
                    Pair(
                        LocalPath.build("/system/framework/settings.jar"),
                        LocalPath.build(base, "system@framework@settings.jar@classes.dex")
                    )
                )
            )
        }
        for (base in dalviksX64) {
            targets.addAll(
                listOf(
                    Pair(
                        LocalPath.build("/system/framework/x64/boot.art"),
                        LocalPath.build(base, "system@framework@boot.art")
                    ),
                    Pair(
                        LocalPath.build("/system/framework/x64/boot.oat"),
                        LocalPath.build(base, "system@framework@boot.oat")
                    ),
                    Pair(
                        LocalPath.build("/system/framework/x64/boot-framework.art"),
                        LocalPath.build(base, "system@framework@boot-framework.art")
                    ),
                    Pair(
                        LocalPath.build("/system/framework/x64/boot-framework.oat"),
                        LocalPath.build(base, "system@framework@boot-framework.oat")
                    ),
                    Pair(
                        LocalPath.build("/system/framework/x64/boot-framework.vdex"),
                        LocalPath.build(base, "system@framework@boot-framework.vdex")
                    ),
                    Pair(
                        LocalPath.build("/system/framework/serviceitems.jar"),
                        LocalPath.build(base, "system@framework@serviceitems.jar@classes.dex")
                    ),
                    Pair(
                        LocalPath.build("/system/framework/settings.jar"),
                        LocalPath.build(base, "system@framework@settings.jar@classes.dex")
                    )
                )
            )
        }
        targets.forEach {
            coEvery { gatewaySwitch.exists(it.first) } returns true
        }

        val processor = getProcessor()

        for (toHit in targets) {
            mockPkg(packageName, toHit.first)
            val areaInfo = processor.identifyArea(toHit.second)!!.apply {
                type shouldBe DataArea.Type.DALVIK_DEX
            }
            processor.findOwners(areaInfo).apply {
                owners.first().pkgId shouldBe packageName
            }
        }
    }

    @Test fun testProcess_hit_custom_apk() = runTest {
        val pkgId = "eu.thedarken.sdm.test".toPkgId()

        val processor = getProcessor()

        for (base in dalviks) {
            val apk = LocalPath.build("/data/app/test-2.apk")
            val apkArchive = mockk<ApkInfo>().apply {
                every { id } returns pkgId
            }
            coEvery { pkgOps.viewArchive(apk, 0) } returns apkArchive

            val target = LocalPath.build(base, "data@app@test-2.apk@classes.dex")

            val areaInfo = processor.identifyArea(target)!!.apply {
                type shouldBe DataArea.Type.DALVIK_DEX
            }
            processor.findOwners(areaInfo).apply {
                owners.first().pkgId shouldBe pkgId
            }
        }
    }

    @Test fun testProcess_clutter_hit() = runTest {
        val packageName = "com.test.pkg".toPkgId()
        mockPkg(packageName)

        val prefixFree = rngString
        mockMarker(packageName, DataArea.Type.DALVIK_DEX, prefixFree)

        val processor = getProcessor()

        for (base in dalviks) {
            val areaInfo = processor.identifyArea(LocalPath.build(base, prefixFree))!!.apply {
                type shouldBe DataArea.Type.DALVIK_DEX
            }
            processor.findOwners(areaInfo).apply {
                owners.first().pkgId shouldBe packageName
            }
        }
    }

    @Test fun testProcess_nothing() = runTest {
        val processor = getProcessor()
        for (base in dalviks) {
            val testFile = LocalPath.build(base, rngString)
            val areaInfo = processor.identifyArea(testFile)!!.apply {
                type shouldBe DataArea.Type.DALVIK_DEX
            }
            processor.findOwners(areaInfo).apply {
                owners shouldBe emptySet()
            }
        }
    }
}