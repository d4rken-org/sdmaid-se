package eu.darken.sdmse.common.forensics.csi.sys

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.removePrefix
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DataCSITest : BaseCSITest() {

    private val baseDataPath1 = LocalPath.build("/data")
    private val baseDataPath2 = LocalPath.build("/mnt/expand", rngString)

    private val storageData1 = DataArea(
        type = DataArea.Type.DATA,
        path = baseDataPath1,
        userHandle = UserHandle2(-1),
    )

    private val storageData2 = DataArea(
        type = DataArea.Type.DATA,
        path = baseDataPath2,
        userHandle = UserHandle2(-1),
    )

    private val storageDataApp1 = DataArea(
        type = DataArea.Type.APP_APP,
        path = LocalPath.build(baseDataPath1, "app"),
        userHandle = storageData1.userHandle,
    )

    private val storageDataApp2 = DataArea(
        type = DataArea.Type.APP_APP,
        path = LocalPath.build(baseDataPath2, "app"),
        userHandle = storageData2.userHandle,
    )

    private val storageDataAppAsec1 = DataArea(
        type = DataArea.Type.APP_ASEC,
        path = LocalPath.build(baseDataPath1, "app-asec"),
        userHandle = storageData1.userHandle,
    )

    private val storageDataAppAsec2 = DataArea(
        type = DataArea.Type.APP_ASEC,
        path = LocalPath.build(baseDataPath2, "app-asec"),
        userHandle = storageData2.userHandle,
    )

    private val storageDataAppPrivate1 = DataArea(
        type = DataArea.Type.APP_APP_PRIVATE,
        path = LocalPath.build(baseDataPath1, "app-private"),
        userHandle = storageData1.userHandle,
    )

    private val storageDataAppPrivate2 = DataArea(
        type = DataArea.Type.APP_APP_PRIVATE,
        path = LocalPath.build(baseDataPath2, "app-private"),
        userHandle = storageData2.userHandle,
    )

    private val storageDataAppLib1 = DataArea(
        type = DataArea.Type.APP_LIB,
        path = LocalPath.build(baseDataPath1, "app-lib"),
        userHandle = storageData1.userHandle,
    )

    private val storageDataAppLib2 = DataArea(
        type = DataArea.Type.APP_LIB,
        path = LocalPath.build(baseDataPath2, "app-lib"),
        userHandle = storageData2.userHandle,
    )

    private val storageDataSystem1 = DataArea(
        type = DataArea.Type.DATA_SYSTEM,
        path = LocalPath.build(baseDataPath1, "system"),
        userHandle = storageData1.userHandle,
    )

    private val storageDataSystem2 = DataArea(
        type = DataArea.Type.DATA_SYSTEM,
        path = LocalPath.build(baseDataPath2, "system"),
        userHandle = storageData2.userHandle,
    )

    private val storageDataSystemCE1 = DataArea(
        type = DataArea.Type.DATA_SYSTEM_CE,
        path = LocalPath.build(baseDataPath1, "system_ce"),
        userHandle = storageData1.userHandle,
    )

    private val storageDataSystemCE2 = DataArea(
        type = DataArea.Type.DATA_SYSTEM_CE,
        path = LocalPath.build(baseDataPath2, "system_ce"),
        userHandle = storageData2.userHandle,
    )

    private val storageDataSystemDE1 = DataArea(
        type = DataArea.Type.DATA_SYSTEM_DE,
        path = LocalPath.build(baseDataPath1, "system_de"),
        userHandle = storageData1.userHandle,
    )

    private val storageDataSystemDE2 = DataArea(
        type = DataArea.Type.DATA_SYSTEM_DE,
        path = LocalPath.build(baseDataPath2, "system_de"),
        userHandle = storageData2.userHandle,
    )

    private val storageDalvikDex1 = DataArea(
        type = DataArea.Type.DALVIK_DEX,
        path = LocalPath.build(baseDataPath1, "dalvik-cache", "arm64"),
        userHandle = storageData1.userHandle,
    )

    private val storageDalvikDex2 = DataArea(
        type = DataArea.Type.DALVIK_DEX,
        path = LocalPath.build(baseDataPath2, "dalvik-cache", "arm64"),
        userHandle = storageData2.userHandle,
    )

    private val storageDalvikProfile1 = DataArea(
        type = DataArea.Type.DALVIK_PROFILE,
        path = LocalPath.build(baseDataPath1, "dalvik-cache", "profiles"),
        userHandle = storageData1.userHandle,
    )

    private val storageDalvikProfile2 = DataArea(
        type = DataArea.Type.DALVIK_PROFILE,
        path = LocalPath.build(baseDataPath2, "dalvik-cache", "profiles"),
        userHandle = storageData2.userHandle,
    )

    private val bases = setOf(
        storageData1.path,
        storageData2.path,
    )

    @Before override fun setup() {
        super.setup()

        every { areaManager.state } returns flowOf(
            DataAreaManager.State(
                areas = setOf(
                    storageData1,
                    storageData2,
                    storageDataApp1,
                    storageDataApp2,
                    storageDataAppAsec1,
                    storageDataAppAsec2,
                    storageDataAppPrivate1,
                    storageDataAppPrivate2,
                    storageDataAppLib1,
                    storageDataAppLib2,
                    storageDataSystem1,
                    storageDataSystem2,
                    storageDataSystemCE1,
                    storageDataSystemCE2,
                    storageDataSystemDE1,
                    storageDataSystemDE2,
                    storageDalvikDex1,
                    storageDalvikDex2,
                    storageDalvikProfile1,
                    storageDalvikProfile2,
                )
            )
        )
    }

    private fun getProcessor() = DataPartitionCSI(
        areaManager = areaManager,
        clutterRepo = clutterRepo,
    )

    @Test override fun `test jurisdiction`() = runTest {
        getProcessor().assertJurisdiction(DataArea.Type.DATA)
    }

    @Test override fun `determine area successfully`() = runTest {
        val processor = getProcessor()

        for (base in bases) {
            val testFile1 = base.child(rngString)
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.DATA
                prefix shouldBe base
                prefixFreeSegments shouldBe testFile1.removePrefix(base)
                isBlackListLocation shouldBe false
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()
        for (base in bases) {

//            processor.identifyArea(base.child( "app", randomString)) shouldBe null
//            processor.identifyArea(base.child( "app-asec", randomString)) shouldBe null
//            processor.identifyArea(base.child( "app-private", randomString)) shouldBe null
//            processor.identifyArea(base.child( "app-lib", randomString)) shouldBe null
//            processor.identifyArea(base.child( "system", randomString)) shouldBe null
//            processor.identifyArea(base.child( "system_ce", randomString)) shouldBe null
//            processor.identifyArea(base.child( "system_de", randomString)) shouldBe null
            processor.identifyArea(base.child("dalvik-cache/arm64", rngString)) shouldBe null
            processor.identifyArea(base.child("dalvik-cache/profiles/", rngString)) shouldBe null
        }
    }

    @Test fun `find owner via direct clutter hit`() = runTest {
        val processor = getProcessor()

        val packageName = "com.test.pkg".toPkgId()
        mockPkg(packageName)

        val prefixFree = rngString
        mockMarker(packageName, DataArea.Type.DATA, prefixFree)

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
            val testFile1 = base.child(rngString)
            val locationInfo1 = processor.identifyArea(testFile1)!!

            processor.findOwners(locationInfo1).apply {
                owners.isEmpty()
                hasKnownUnknownOwner shouldBe false
            }
        }
    }

}