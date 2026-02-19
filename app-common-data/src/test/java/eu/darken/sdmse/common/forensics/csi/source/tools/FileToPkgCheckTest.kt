package eu.darken.sdmse.common.forensics.csi.source.tools

import eu.darken.sdmse.common.files.local.LocalPath
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

class FileToPkgCheckTest : BaseTest() {

    @MockK lateinit var pkgRepo: PkgRepo
    @Before fun setup() {
        MockKAnnotations.init(this)
        coEvery { pkgRepo.query(any(), any()) } returns emptySet()
    }

    private fun create() = FileToPkgCheck(pkgRepo)

    @Test fun testNotInstalled() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("com.mxtech.ffmpeg.x86-123.apk")
            every { userHandle } returns UserHandle2(0)
        }

        create().process(areaInfo).owners.isEmpty() shouldBe true
    }

    @Test fun testBaseMatch() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("com.mxtech.ffmpeg.x86-123.apk")
            every { userHandle } returns UserHandle2(0)
        }
        val testPkg = "com.mxtech.ffmpeg.x86".toPkgId()
        coEvery { pkgRepo.query(testPkg, UserHandle2(0)) } returns setOf(mockk())

        create().process(areaInfo).owners.single().pkgId shouldBe testPkg
    }
}