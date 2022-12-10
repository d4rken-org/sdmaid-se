package eu.darken.sdmse.common.forensics.csi.dalvik

import eu.darken.sdmse.common.Architecture
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.forensics.csi.dalvik.tools.*
import eu.darken.sdmse.common.randomString
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
    }
    private val storageDalvikProfileX641 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DALVIK_DEX
        every { path } returns dalvik2
    }
    private val storageDalvikProfileX862 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DALVIK_DEX
        every { path } returns dalvik3
    }
    private val storageDalvikProfileX642 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DALVIK_DEX
        every { path } returns dalvik4
    }
    private val storageData1 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_APP
        every { path } returns LocalPath.build("/data/app")
    }
    private val storageData2 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_APP
        every { path } returns LocalPath.build("/mnt/expand/uuid/app")
    }
    private val storageSystem = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.SYSTEM
        every { path } returns LocalPath.build("/system")
    }
    private val storageSystemApp = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.SYSTEM_APP
        every { path } returns LocalPath.build("/system/app")
    }
    private val storageSystemPrivApp = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.SYSTEM_PRIV_APP
        every { path } returns LocalPath.build("/system/priv-app")
    }
    private val storageVendor = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.SYSTEM
        every { path } returns LocalPath.build("/vendor")
    }

    @Before override fun setup() {
        super.setup()
        every { areaManager.areas } returns flowOf(
            setOf(
                storageData1,
                storageData2,
                storageSystem,
                storageSystemApp,
                storageSystemPrivApp,
                storageDalvikProfileX861,
                storageDalvikProfileX641,
                storageDalvikProfileX862,
                storageDalvikProfileX642,
                storageVendor
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
            val testFile1 = LocalPath.build(path, randomString())
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.DALVIK_DEX
                prefix shouldBe "${path.path}/"
                prefixFreePath shouldBe testFile1.name
                isBlackListLocation shouldBe true
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()

        processor.identifyArea(LocalPath.build("/data/dalvik-cache", randomString())) shouldBe null
        processor.identifyArea(LocalPath.build("/data/dalvik-cache/arm", randomString())) shouldBe null

        processor.identifyArea(LocalPath.build("/cache/dalvik-cache/profiles/random")) shouldBe null
        processor.identifyArea(LocalPath.build("/data/dalvik-cache/profiles/random")) shouldBe null

        processor.identifyArea(LocalPath.build("/cache/dalvik-cache/something64/random")) shouldBe null
        processor.identifyArea(LocalPath.build("/data/dalvik-cache/something64/random")) shouldBe null
    }


    @Test fun testDetermineLocation_unknown() = runTest {
        val processor = getProcessor()
        for (base in dalvikCachesBases) {
            processor.identifyArea(LocalPath.build(base, randomString())) shouldBe null
            processor.identifyArea(LocalPath.build(base, "/profiles", randomString())) shouldBe null
            processor.identifyArea(LocalPath.build(base, "/something64", randomString())) shouldBe null
        }
    }
//
//    @Test fun testProcess_hit() {
//        val packageName = "com.test.pkg"
//        for (base in dalviks) {
//            val targets: Collection<Pair<out SDMFile?, out SDMFile?>> = Arrays.asList(
//                Pair<F, S>(
//                    LocalPath.build("/data/app/com.test.pkg-1.apk"),
//                    LocalPath.build(base, "data@app@com.test.pkg-1.apk@classes.dex")
//                ),
//                Pair<F, S>(
//                    LocalPath.build("/data/app/com.test.pkg-12.apk"),
//                    LocalPath.build(base, "data@app@com.test.pkg-12.apk@classes.dex")
//                ),
//                Pair<F, S>(
//                    LocalPath.build("/mnt/expand/uuid/app/com.test.pkg-12.apk"),
//                    LocalPath.build(base, "mnt@expand@uuid@app@com.test.pkg-12.apk@classes.dex")
//                ),
//                Pair<F, S>(
//                    LocalPath.build("/data/app/com.test.pkg-12.apk"),
//                    LocalPath.build(base, "data@app@com.test.pkg-12.apk@classes.odex")
//                ),
//                Pair<F, S>(
//                    LocalPath.build("/data/app/com.test.pkg-12.apk"),
//                    LocalPath.build(base, "data@app@com.test.pkg-12.apk@classes.dex.art")
//                ),
//                Pair<F, S>(
//                    LocalPath.build("/data/app/com.test.pkg-12.apk"),
//                    LocalPath.build(base, "data@app@com.test.pkg-12.apk@classes.oat")
//                ),
//                Pair<F, S>(
//                    LocalPath.build("/data/app/com.test.pkg-12.apk"),
//                    LocalPath.build(base, "data@app@com.test.pkg-12.apk.dex")
//                ),
//                Pair<F, S>(
//                    LocalPath.build("/data/app/com.test.pkg-12.apk"),
//                    LocalPath.build(base, "data@app@com.test.pkg-12.apk.odex")
//                ),
//                Pair<F, S>(
//                    LocalPath.build("/data/app/com.test.pkg-12.apk"),
//                    LocalPath.build(base, "data@app@com.test.pkg-12.apk.oat")
//                ),
//                Pair<F, S>(
//                    LocalPath.build("/data/app/com.test.pkg-12.apk"),
//                    LocalPath.build(base, "data@app@com.test.pkg-12.apk.vdex")
//                ),
//                Pair<F, S>(
//                    LocalPath.build("/system/framework/com.test.pkg-2.apk"),
//                    LocalPath.build(base, "system@framework@com.test.pkg-2.jar@classes.odex")
//                ),
//                Pair<F, S>(
//                    LocalPath.build("/system/app/com.test.pkg-2.jar"),
//                    LocalPath.build(base, "system@app@com.test.pkg-2.apk@classes.dex")
//                ),
//                Pair<F, S>(
//                    LocalPath.build("/system/app/Wallet/Wallet.apk"),
//                    LocalPath.build(base, "system@app@Wallet@Wallet.apk@classes.dex")
//                ),
//                Pair<F, S>(
//                    LocalPath.build("/system/priv-app/NetworkRecommendation/NetworkRecommendation.apk"),
//                    LocalPath.build(base, "/system@priv-app@NetworkRecommendation@NetworkRecommendation.apk@classes.dex")
//                        
//                ),
//                Pair<F, S>(
//                    LocalPath.build("/system/priv-app/NetworkRecommendation/NetworkRecommendation.apk"),
//                    LocalPath.build(base, "/system@priv-app@NetworkRecommendation@NetworkRecommendation.apk@classes.vdex")
//                        
//                )
//            )
//            for (toHit in targets) {
//                setupApp(packageName, toHit.first)
//                val locationInfo: LocationInfo = csiModule.matchLocation(toHit.second)
//                val ownerInfo = OwnerInfo(locationInfo)
//                csiModule.process(ownerInfo)
//                ownerInfo.checkOwnerState(fileForensics)
//                MatcherAssert.assertThat(
//                    toHit.second.toString() + " doesn't match " + toHit.first.toString(),
//                    ownerInfo.isCurrentlyOwned(),
//                    Is.`is`(true)
//                )
//                assertEquals(1, ownerInfo.getOwners().size())
//                assertEquals(packageName, ownerInfo.getOwners().get(0).getPackageName())
//            }
//        }
//    }
//
//    @Test fun testProcess_hit_unknown_owner() {
//        val packageName = "com.test.pkg"
//        val targets: MutableCollection<Pair<out SDMFile?, out SDMFile?>> = ArrayList<Pair<out SDMFile?, out SDMFile?>>()
//        for (base in dalviksX86) {
//            targets.addAll(
//                Arrays.asList(
//                    Pair<F, S>(
//                        LocalPath.build("/system/framework/x86/boot.art"),
//                        LocalPath.build(base, "system@framework@boot.art")
//                    ),
//                    Pair<F, S>(
//                        LocalPath.build("/system/framework/x86/boot.oat"),
//                        LocalPath.build(base, "system@framework@boot.oat")
//                    ),
//                    Pair<F, S>(
//                        LocalPath.build("/system/framework/x86/boot-framework.art"),
//                        LocalPath.build(base, "system@framework@boot-framework.art")
//                    ),
//                    Pair<F, S>(
//                        LocalPath.build("/system/framework/x86/boot-framework.oat"),
//                        LocalPath.build(base, "system@framework@boot-framework.oat")
//                    ),
//                    Pair<F, S>(
//                        LocalPath.build("/system/framework/x86/boot-framework.vdex"),
//                        LocalPath.build(base, "system@framework@boot-framework.vdex")
//                    ),
//                    Pair<F, S>(
//                        LocalPath.build("/system/framework/serviceitems.jar"),
//                        LocalPath.build(base, "system@framework@serviceitems.jar@classes.dex")
//                    ),
//                    Pair<F, S>(
//                        LocalPath.build("/system/framework/settings.jar"),
//                        LocalPath.build(base, "system@framework@settings.jar@classes.dex")
//                    )
//                )
//            )
//        }
//        for (base in dalviksX64) {
//            targets.addAll(
//                Arrays.asList(
//                    Pair<F, S>(
//                        LocalPath.build("/system/framework/x64/boot.art"),
//                        LocalPath.build(base, "system@framework@boot.art")
//                    ),
//                    Pair<F, S>(
//                        LocalPath.build("/system/framework/x64/boot.oat"),
//                        LocalPath.build(base, "system@framework@boot.oat")
//                    ),
//                    Pair<F, S>(
//                        LocalPath.build("/system/framework/x64/boot-framework.art"),
//                        LocalPath.build(base, "system@framework@boot-framework.art")
//                    ),
//                    Pair<F, S>(
//                        LocalPath.build("/system/framework/x64/boot-framework.oat"),
//                        LocalPath.build(base, "system@framework@boot-framework.oat")
//                    ),
//                    Pair<F, S>(
//                        LocalPath.build("/system/framework/x64/boot-framework.vdex"),
//                        LocalPath.build(base, "system@framework@boot-framework.vdex")
//                    ),
//                    Pair<F, S>(
//                        LocalPath.build("/system/framework/serviceitems.jar"),
//                        LocalPath.build(base, "system@framework@serviceitems.jar@classes.dex")
//                    ),
//                    Pair<F, S>(
//                        LocalPath.build("/system/framework/settings.jar"),
//                        LocalPath.build(base, "system@framework@settings.jar@classes.dex")
//                    )
//                )
//            )
//        }
//        for (toHit in targets) {
//            setupApp(packageName, toHit.first)
//            val locationInfo: LocationInfo = csiModule.matchLocation(toHit.second)
//            val ownerInfo = OwnerInfo(locationInfo)
//            csiModule.process(ownerInfo)
//            ownerInfo.checkOwnerState(fileForensics)
//            MatcherAssert.assertThat(
//                toHit.second.toString() + " doesn't match " + toHit.first.toString(),
//                ownerInfo.isCurrentlyOwned(),
//                Is.`is`(true)
//            )
//        }
//    }
//
//    @Test fun testProcess_hit_child() {}
//    @Test fun testProcess_hit_default_unknowns() {
//        val packageName = "com.test.pkg"
//        for (base in dalviks) {
//            val targets: Collection<Pair<out SDMFile?, out SDMFile?>> = listOf(
//                Pair<F, S>(LocalPath.build(""), LocalPath.build(base, "minimode.dex"))
//            )
//            for (toHit in targets) {
//                setupApp(packageName, toHit.first)
//                val locationInfo: LocationInfo = csiModule.matchLocation(toHit.second)
//                val ownerInfo = OwnerInfo(locationInfo)
//                csiModule.process(ownerInfo)
//                ownerInfo.checkOwnerState(fileForensics)
//                Assert.assertTrue(toHit.first.toString(), ownerInfo.isCurrentlyOwned())
//            }
//        }
//    }
//
//    @Test fun testProcess_hit_custom_apk() {
//        val packageInfo: SDMPkgInfo = Mockito.mock(SDMPkgInfo::class.java)
//        Mockito.`when`(packageInfo.getPackageName()).thenReturn("eu.thedarken.sdm.test")
//        setupApp(packageInfo.getPackageName(), null)
//        for (base in dalviks) {
//            val apk: SDMFile = LocalPath.build("/data/app/test-2.apk")
//            val target: SDMFile = LocalPath.build(base, "data@app@test-2.apk@classes.dex")
//            Mockito.`when`(ipcFunnel.submit(ArgumentMatchers.any(IPCFunnel.ArchiveQuery::class.java)))
//                .thenAnswer(label@ Answer { invocation: InvocationOnMock ->
//                    val query: IPCFunnel.ArchiveQuery = invocation.getArgument(0)
//                    if (query.getPath().equals(apk.getPath())) return@label packageInfo
//                    null
//                } as Answer<SDMPkgInfo?>)
//            val locationInfo: LocationInfo = csiModule.matchLocation(target)
//            val ownerInfo = OwnerInfo(locationInfo)
//            csiModule.process(ownerInfo)
//            ownerInfo.checkOwnerState(fileForensics)
//            MatcherAssert.assertThat(ownerInfo.toString(), ownerInfo.isCurrentlyOwned(), Is.`is`(true))
//            assertEquals(1, ownerInfo.getOwners().size())
//            assertEquals(packageInfo.getPackageName(), ownerInfo.getOwners().get(0).getPackageName())
//        }
//    }
//
//    @Test fun testProcess_clutter_hit() {
//        val packageName = "com.test.pkg"
//        setupApp(packageName, null)
//        val prefixFree = randomString()
//        addMarker(packageName, Location.DALVIK_DEX, prefixFree)
//        for (base in dalviks) {
//            val toHit: SDMFile = LocalPath.build(base, prefixFree)
//            val locationInfo: LocationInfo = csiModule.matchLocation(toHit)
//            Assert.assertNotNull(locationInfo)
//            assertEquals(base.getPath() + File.separator, locationInfo.getPrefix())
//            val ownerInfo = OwnerInfo(locationInfo)
//            csiModule.process(ownerInfo)
//            ownerInfo.checkOwnerState(fileForensics)
//            assertEquals(1, ownerInfo.getOwners().size())
//            assertEquals(packageName, ownerInfo.getOwners().get(0).getPackageName())
//            Assert.assertTrue(ownerInfo.isCurrentlyOwned())
//        }
//    }
//
//    @Test fun testProcess_nothing() {
//        for (base in dalviks) {
//            val testFile1: SDMFile = LocalPath.build(base, randomString())
//            val locationInfo1: LocationInfo = csiModule.matchLocation(testFile1)
//            val ownerInfo1 = OwnerInfo(locationInfo1)
//            csiModule.process(ownerInfo1)
//            ownerInfo1.checkOwnerState(fileForensics)
//            Assert.assertTrue(ownerInfo1.isCorpse())
//            Assert.assertFalse(ownerInfo1.isCurrentlyOwned())
//        }
//    }
}