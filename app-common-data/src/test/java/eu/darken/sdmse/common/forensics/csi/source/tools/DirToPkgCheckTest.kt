package eu.darken.sdmse.common.forensics.csi.source.tools

import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.user.UserHandle2
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

class DirToPkgCheckTest : BaseTest() {

    @MockK lateinit var pkgRepo: PkgRepo
    private val handle = UserHandle2(0)
    @Before fun setup() {
        MockKAnnotations.init(this)
        coEvery { pkgRepo.query(any(), any()) } returns emptySet()
    }

    private fun create() = DirToPkgCheck(pkgRepo)

    @Test fun testDontMatchEmpty() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { prefixFreeSegments } returns emptyList()
            every { userHandle } returns handle
        }

        create().process(areaInfo).owners.isEmpty() shouldBe true
    }

    @Test fun testDontMatchUninstalled() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { prefixFreeSegments } returns emptyList()
            every { userHandle } returns handle
        }
        val testPkg = "com.mxtech.ffmpeg.x86".toPkgId()
        coEvery { pkgRepo.query(testPkg, handle) } returns setOf(mockk())

        create().process(areaInfo).owners.isEmpty() shouldBe true
    }

    @Test fun testNested() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { prefixFreeSegments } returns listOf("com.mxtech.ffmpeg.x86-1234", "something")
            every { userHandle } returns handle
        }
        val testPkg = "com.mxtech.ffmpeg.x86".toPkgId()
        coEvery { pkgRepo.query(testPkg, handle) } returns setOf(mockk())

        create().process(areaInfo).owners.single().pkgId shouldBe testPkg
    }

    @Test fun testPreOreoMatch() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { prefixFreeSegments } returns listOf("com.mxtech.ffmpeg.x86-1234")
            every { userHandle } returns handle
        }
        val testPkg = "com.mxtech.ffmpeg.x86".toPkgId()
        coEvery { pkgRepo.query(testPkg, handle) } returns setOf(mockk())

        create().process(areaInfo).owners.single().pkgId shouldBe testPkg
    }

    @Test fun testPostOreoMatch() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { prefixFreeSegments } returns listOf("com.mxtech.ffmpeg.x86-tmEGrx2zM5CeRFI72KWLSA==")
            every { userHandle } returns handle
        }
        val testPkg = "com.mxtech.ffmpeg.x86".toPkgId()
        coEvery { pkgRepo.query(testPkg, handle) } returns setOf(mockk())

        create().process(areaInfo).owners.single().pkgId shouldBe testPkg
    }
}