package eu.darken.sdmse.common.forensics.csi.source.tools

import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import testhelpers.BaseTest
import java.io.File

class DirToPkgCheckTest : BaseTest() {

    @MockK lateinit var pkgRepo: eu.darken.sdmse.common.pkgs.PkgRepo

    @Before fun setup() {
        MockKAnnotations.init(this)
        coEvery { pkgRepo.isInstalled(any()) } returns false
    }

    private fun create() = DirToPkgCheck(pkgRepo)

    @Test fun testDontMatchEmpty() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { prefixFreePath } returns ""
        }

        create().process(areaInfo).owners.isEmpty() shouldBe true
    }

    @Test fun testDontMatchUninstalled() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { prefixFreePath } returns ""
        }
        val testPkg = "com.mxtech.ffmpeg.x86".toPkgId()
        coEvery { pkgRepo.isInstalled(testPkg) } returns true

        create().process(areaInfo).owners.isEmpty() shouldBe true
    }

    @Test fun testNested() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { prefixFreePath } returns "com.mxtech.ffmpeg.x86-1234${File.separator}something"
        }
        val testPkg = "com.mxtech.ffmpeg.x86".toPkgId()
        coEvery { pkgRepo.isInstalled(testPkg) } returns true

        create().process(areaInfo).owners.single().pkgId shouldBe testPkg
    }

    @Test fun testPreOreoMatch() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { prefixFreePath } returns "com.mxtech.ffmpeg.x86-1234"
        }
        val testPkg = "com.mxtech.ffmpeg.x86".toPkgId()
        coEvery { pkgRepo.isInstalled(testPkg) } returns true

        create().process(areaInfo).owners.single().pkgId shouldBe testPkg
    }

    @Test fun testPostOreoMatch() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { prefixFreePath } returns "com.mxtech.ffmpeg.x86-tmEGrx2zM5CeRFI72KWLSA=="
        }
        val testPkg = "com.mxtech.ffmpeg.x86".toPkgId()
        coEvery { pkgRepo.isInstalled(testPkg) } returns true

        create().process(areaInfo).owners.single().pkgId shouldBe testPkg
    }
}