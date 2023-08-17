package eu.darken.sdmse.common.forensics.csi.sys

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.removePrefix
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.rngString
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DownloadCacheCSITest : BaseCSITest() {

    private val cacheArea = mockk<DataArea>().apply {
        every { type } returns DataArea.Type.DOWNLOAD_CACHE
        every { path } returns LocalPath.build("/cache")
        every { flags } returns emptySet()
    }

    private val cacheAreas = setOf(cacheArea)
    private val basePaths = cacheAreas.map { it.path }

    @Before override fun setup() {
        super.setup()

        every { areaManager.state } returns flowOf(
            DataAreaManager.State(
                areas = setOf(
                    cacheArea,
                )
            )
        )
    }

    private fun getProcessor() = DownloadCacheCSI(
        areaManager = areaManager,
    )

    @Test override fun `test jurisdiction`() = runTest {
        getProcessor().assertJurisdiction(DataArea.Type.DOWNLOAD_CACHE)
    }

    @Test override fun `determine area successfully`() = runTest {
        val processor = getProcessor()

        for (base in basePaths) {
            val testFile1 = base.child(rngString)
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.DOWNLOAD_CACHE
                prefix shouldBe base
                prefixFreeSegments shouldBe testFile1.removePrefix(base)
                isBlackListLocation shouldBe false
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()

        processor.identifyArea(LocalPath.build("/data", rngString)) shouldBe null
        processor.identifyArea(LocalPath.build("/")) shouldBe null
    }

    @Test fun `find no owner or fallback`() = runTest {
        val processor = getProcessor()

        for (base in basePaths) {
            val testFile1 = base.child(rngString)
            val areaInfo = processor.identifyArea(testFile1)!!

            processor.findOwners(areaInfo).apply {
                owners shouldBe emptySet()
                hasKnownUnknownOwner shouldBe false
            }
        }
    }
}