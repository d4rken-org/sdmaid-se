package eu.darken.sdmse.common.forensics.csi.source.tools

import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.pkgs.PkgRepo
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

class LuckyPatcherCheckTest : BaseTest() {

    @MockK lateinit var pkgRepo: PkgRepo

    @Before fun setup() {
        MockKAnnotations.init(this)
        coEvery { pkgRepo.isInstalled(any()) } returns false
    }

    private fun create() = LuckyPatcherCheck(pkgRepo)

    @Test fun `pkg is installed`() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("some.pkg-123.dex")
        }
        val pkgId = "some.pkg".toPkgId()
        coEvery { pkgRepo.isInstalled(pkgId) } returns true

        create().process(areaInfo).owners.single().pkgId shouldBe pkgId
    }

    @Test fun `pkg is not installed`() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("some.pkg-123.dex")
        }

        coEvery { pkgRepo.isInstalled(any()) } returns false

        create().process(areaInfo).owners.size shouldBe 0

    }

    @Test fun testBaseMatch_dex() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("some.pkg-123.dex")
        }

        val pkgId = "some.pkg".toPkgId()
        coEvery { pkgRepo.isInstalled(pkgId) } returns true
        val pkgId2 = "com.chelpus.lackypatch".toPkgId()
        coEvery { pkgRepo.isInstalled(pkgId2) } returns true

        create().process(areaInfo).owners shouldBe setOf(
            Owner(pkgId),
            Owner(pkgId2, setOf(Marker.Flag.CUSTODIAN))
        )
    }

    @Test fun testBaseMatch_odex() = runTest {
        val areaInfo = mockk<AreaInfo>().apply {
            every { file } returns LocalPath.build("some.pkg-123.odex")
        }

        val pkgId = "some.pkg".toPkgId()
        coEvery { pkgRepo.isInstalled(pkgId) } returns true
        val pkgId2 = "com.forpda.lp".toPkgId()
        coEvery { pkgRepo.isInstalled(pkgId2) } returns true

        create().process(areaInfo).owners shouldBe setOf(
            Owner(pkgId),
            Owner(pkgId2, setOf(Marker.Flag.CUSTODIAN))
        )
    }
}