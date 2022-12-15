package eu.darken.sdmse.common.forensics.csi.misc

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

class DataSDExt2CSITest : BaseCSITest() {

    private val dataPath1 = LocalPath.build("/data")
    private val dataPaths = setOf(
        dataPath1
    )

    private val storageData1 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DATA
        every { path } returns dataPath1
    }

    private val storageDataSystem1 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DATA_SYSTEM
        every { path } returns LocalPath.build(dataPath1, "system")
    }

    private val storageDataSystemCE1 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DATA_SYSTEM_CE
        every { path } returns LocalPath.build(dataPath1, "system_ce")
    }

    private val storageDataSystemDE1 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DATA_SYSTEM_DE
        every { path } returns LocalPath.build(dataPath1, "system_de")
    }

    private val storageDataSdExt1 = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.DATA_SDEXT2
        every { path } returns LocalPath.build(dataPath1, "sdext2")
    }

    val sdexts = setOf(
        storageDataSdExt1.path
    ).map { it as LocalPath }

    @Before override fun setup() {
        super.setup()

        every { areaManager.state } returns flowOf(
            DataAreaManager.State(
                areas = setOf(
                    storageData1,
                    storageDataSystem1,
                    storageDataSystemCE1,
                    storageDataSystemDE1,
                    storageDataSdExt1,
                )
            )
        )
    }

    private fun getProcessor() = DataSDExt2CSI(
        areaManager = areaManager,
    )

    @Test override fun `test jurisdiction`() = runTest {
        getProcessor().assertJurisdiction(DataArea.Type.DATA_SDEXT2)
    }

    @Test override fun `determine area successfully`() = runTest {
        val processor = getProcessor()

        for (base in sdexts) {
            val testFile1 = LocalPath.build(base, randomString())
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.DATA_SDEXT2
                prefix shouldBe "${base.path}/"
                prefixFreePath shouldBe testFile1.name
                isBlackListLocation shouldBe false
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()
        for (base in dataPaths) {

            processor.identifyArea(LocalPath.build(base, "app", randomString())) shouldBe null
            processor.identifyArea(LocalPath.build(base, "app-asec", randomString())) shouldBe null
            processor.identifyArea(LocalPath.build(base, "app-private", randomString())) shouldBe null
            processor.identifyArea(LocalPath.build(base, "app-lib", randomString())) shouldBe null
            processor.identifyArea(LocalPath.build(base, "", randomString())) shouldBe null
            processor.identifyArea(LocalPath.build(base, "system_de", randomString())) shouldBe null
            processor.identifyArea(LocalPath.build(base, "system_ce", randomString())) shouldBe null
            processor.identifyArea(LocalPath.build(base, "sdext2")) shouldBe null
            processor.identifyArea(LocalPath.build(base, "sdext2", randomString())) shouldNotBe null
        }
    }

    @Test fun `find no owner or fallback`() = runTest {
        val processor = getProcessor()

        for (base in sdexts) {
            val testFile1 = LocalPath.build(base, randomString())
            val locationInfo1 = processor.identifyArea(testFile1)!!

            processor.findOwners(locationInfo1).apply {
                owners.isEmpty()
                hasKnownUnknownOwner shouldBe false
            }
        }
    }

}