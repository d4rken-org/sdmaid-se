package eu.darken.sdmse.common.forensics.csi.sys

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.randomString
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DataSystemCSITest : BaseCSITest() {

    private val baseDataPath1 = LocalPath.build("/data")
    private val baseDataPath2 = LocalPath.build("/mnt/expand", randomString())

    private val storageData1 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DATA
        every { path } returns baseDataPath1
    }

    private val storageData2 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DATA
        every { path } returns baseDataPath2
    }

    private val storageDataApp1 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_APP
        every { path } returns LocalPath.build(baseDataPath1, "app")
    }

    private val storageDataApp2 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_APP
        every { path } returns LocalPath.build(baseDataPath2, "app")
    }

    private val storageDataAppAsec1 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_ASEC
        every { path } returns LocalPath.build(baseDataPath1, "app-asec")
    }

    private val storageDataAppAsec2 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_ASEC
        every { path } returns LocalPath.build(baseDataPath2, "app-asec")
    }

    private val storageDataAppPrivate1 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_APP_PRIVATE
        every { path } returns LocalPath.build(baseDataPath1, "app-private")
    }

    private val storageDataAppPrivate2 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_APP_PRIVATE
        every { path } returns LocalPath.build(baseDataPath2, "app-private")
    }

    private val storageDataAppLib1 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_LIB
        every { path } returns LocalPath.build(baseDataPath1, "app-lib")
    }

    private val storageDataAppLib2 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_LIB
        every { path } returns LocalPath.build(baseDataPath2, "app-lib")
    }

    private val storageDataSystem1 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DATA_SYSTEM
        every { path } returns LocalPath.build(baseDataPath1, "system")
    }

    private val storageDataSystem2 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DATA_SYSTEM
        every { path } returns LocalPath.build(baseDataPath2, "system")
    }

    private val storageDataSystemCE1 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DATA_SYSTEM_CE
        every { path } returns LocalPath.build(baseDataPath1, "system_ce")
    }

    private val storageDataSystemCE2 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DATA_SYSTEM_CE
        every { path } returns LocalPath.build(baseDataPath2, "system_ce")
    }

    private val storageDataSystemDE1 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DATA_SYSTEM_DE
        every { path } returns LocalPath.build(baseDataPath1, "system_de")
    }

    private val storageDataSystemDE2 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DATA_SYSTEM_DE
        every { path } returns LocalPath.build(baseDataPath2, "system_de")
    }

    private val bases = setOf(
        storageDataSystem1.path,
        storageDataSystem2.path,
    ).map { it as LocalPath }

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
                )
            )
        )
    }

    private fun getProcessor() = DataSystemCSI(
        areaManager = areaManager,
    )

    @Test override fun `test jurisdiction`() = runTest {
        getProcessor().assertJurisdiction(DataArea.Type.DATA_SYSTEM)
    }

    @Test override fun `determine area successfully`() = runTest {
        val processor = getProcessor()

        for (base in bases) {
            val testFile1 = LocalPath.build(base, randomString())
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.DATA_SYSTEM
                prefix shouldBe "${base.path}/"
                prefixFreePath shouldBe testFile1.name
                isBlackListLocation shouldBe false
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()
        for (base in setOf(storageData1, storageData2).map { it.path as LocalPath }) {

            processor.identifyArea(LocalPath.build(base, "app", randomString())) shouldBe null
            processor.identifyArea(LocalPath.build(base, "app-asec", randomString())) shouldBe null
            processor.identifyArea(LocalPath.build(base, "app-private", randomString())) shouldBe null
            processor.identifyArea(LocalPath.build(base, "app-lib", randomString())) shouldBe null
            processor.identifyArea(LocalPath.build(base, "", randomString())) shouldBe null
            processor.identifyArea(LocalPath.build(base, "system_de", randomString())) shouldBe null
            processor.identifyArea(LocalPath.build(base, "system_ce", randomString())) shouldBe null
            processor.identifyArea(LocalPath.build(base, "system", randomString())) shouldNotBe null
        }
    }

    @Test fun `find no owner or fallback`() = runTest {
        val processor = getProcessor()

        for (base in bases) {
            val testFile1 = LocalPath.build(base, randomString())
            val locationInfo1 = processor.identifyArea(testFile1)!!

            processor.findOwners(locationInfo1).apply {
                owners.isEmpty()
                hasKnownUnknownOwner shouldBe false
            }
        }
    }
}