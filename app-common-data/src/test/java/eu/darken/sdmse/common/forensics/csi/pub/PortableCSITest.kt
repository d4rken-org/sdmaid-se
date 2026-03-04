package eu.darken.sdmse.common.forensics.csi.pub

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.removePrefix
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.rngString
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
class PortableCSITest : BaseCSITest() {
    val basePortablePath1 = LocalPath.build("/usbstick1")
    val basePortablePath2 = LocalPath.build("/usbstick2")
    val portablePaths = setOf(basePortablePath1, basePortablePath2)

    val portableStorage1 = mockk<DataArea>().apply {
        every { path } returns basePortablePath1
        every { type } returns DataArea.Type.PORTABLE
        every { flags } returns emptySet()
    }

    val portableStorage2 = mockk<DataArea>().apply {
        every { path } returns basePortablePath2
        every { type } returns DataArea.Type.PORTABLE
        every { flags } returns emptySet()
    }

    val storageSdcard1 = mockk<DataArea>().apply {
        every { path } returns LocalPath.build("/sdcard")
        every { type } returns DataArea.Type.SDCARD
        every { flags } returns emptySet()
    }

    val storagePublicData1 = mockk<DataArea>().apply {
        every { path } returns LocalPath.build("/sdcard/", "Android/data")
        every { type } returns DataArea.Type.PUBLIC_DATA
        every { flags } returns emptySet()
    }

    @Before override fun setup() {
        MockKAnnotations.init(this)
        super.setup()

        every { areaManager.state } returns flowOf(
            DataAreaManager.State(
                areas = setOf(
                    storageSdcard1,
                    storagePublicData1,
                    portableStorage1,
                    portableStorage2,
                )
            )
        )
    }

    private fun getProcessor() = PortableCSI(
        areaManager = areaManager,
    )

    @Test override fun `test jurisdiction`() = runTest {
        getProcessor().assertJurisdiction(DataArea.Type.PORTABLE)
    }

    @Test override fun `determine area successfully`() = runTest {
        val processor = getProcessor()
        for (base in portablePaths) {
            val testFile1 = base.child(rngString)
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.PORTABLE
                prefixFreeSegments shouldBe testFile1.removePrefix(base)
                prefix shouldBe base
                isBlackListLocation shouldBe false
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()
        for (base in portablePaths) {
            processor.identifyArea(base) shouldBe null
        }
    }


}