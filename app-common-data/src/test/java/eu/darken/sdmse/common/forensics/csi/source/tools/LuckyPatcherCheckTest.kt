package eu.darken.sdmse.common.forensics.csi.source.tools

import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
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

class LuckyPatcherCheckTest : BaseTest() {

    @MockK lateinit var pkgRepo: PkgRepo
    private val handle = UserHandle2(0)
    @Before fun setup() {
        MockKAnnotations.init(this)
        coEvery { pkgRepo.query(any(), any()) } returns emptySet()
    }

    private fun create() = LuckyPatcherCheck(pkgRepo)

    @Test fun `pkg is installed`() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("some.pkg-123.dex")
            every { userHandle } returns handle
        }
        val pkgId = "some.pkg".toPkgId()
        coEvery { pkgRepo.query(pkgId, handle) } returns setOf(mockk())

        create().process(areaInfo).owners.single().pkgId shouldBe pkgId
    }

    @Test fun `pkg is not installed`() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("some.pkg-123.dex")
            every { userHandle } returns handle
        }

        coEvery { pkgRepo.query(any(), any()) } returns emptySet()

        create().process(areaInfo).owners.size shouldBe 0

    }

    @Test fun testBaseMatch_dex() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("some.pkg-123.dex")
            every { userHandle } returns handle
        }

        val pkgId = "some.pkg".toPkgId()
        coEvery { pkgRepo.query(pkgId, handle) } returns setOf(mockk())
        val pkgId2 = "com.chelpus.lackypatch".toPkgId()
        coEvery { pkgRepo.query(pkgId2, handle) } returns setOf(mockk())

        create().process(areaInfo).owners shouldBe setOf(
            Owner(pkgId, handle),
            Owner(pkgId2, handle, setOf(Marker.Flag.CUSTODIAN))
        )
    }

    @Test fun testBaseMatch_odex() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("some.pkg-123.odex")
            every { userHandle } returns handle
        }

        val pkgId = "some.pkg".toPkgId()
        coEvery { pkgRepo.query(pkgId, handle) } returns setOf(mockk())
        val pkgId2 = "com.forpda.lp".toPkgId()
        coEvery { pkgRepo.query(pkgId2, handle) } returns setOf(mockk())

        create().process(areaInfo).owners shouldBe setOf(
            Owner(pkgId, handle),
            Owner(pkgId2, handle, setOf(Marker.Flag.CUSTODIAN))
        )
    }
}