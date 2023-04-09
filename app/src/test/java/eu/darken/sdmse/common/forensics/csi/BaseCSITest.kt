package eu.darken.sdmse.common.forensics.csi

import android.os.storage.StorageManager
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.clutter.ClutterRepo
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.CSIProcessor
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import testhelpers.BaseTest

abstract class BaseCSITest : BaseTest() {

    @MockK lateinit var pkgRepo: PkgRepo
    @MockK lateinit var areaManager: DataAreaManager
    @MockK lateinit var clutterRepo: ClutterRepo
    @MockK lateinit var storageManager: StorageManager
    @MockK lateinit var gatewaySwitch: GatewaySwitch
    @MockK lateinit var userManager2: UserManager2
    @MockK lateinit var storageEnvironment: StorageEnvironment
    @MockK lateinit var pkgOps: PkgOps

    private val pkgs = mutableSetOf<Installed>()

    @BeforeEach
    open fun setup() {
        if (!::pkgOps.isInitialized) {
            MockKAnnotations.init(this)
        }
        coEvery { clutterRepo.match(any(), any()) } returns emptySet()
        coEvery { gatewaySwitch.listFiles(any()) } returns emptyList()
        coEvery { gatewaySwitch.exists(any()) } returns false
        coEvery { userManager2.currentUser() } returns UserProfile2(UserHandle2(0))
        every { storageEnvironment.dataDir } returns LocalPath.build("/data")

        coEvery { pkgRepo.query(any(), any()) } returns emptySet()
        every { pkgRepo.pkgs } returns flowOf(pkgs)
        coEvery { pkgOps.viewArchive(any(), any()) } returns null
    }

    @AfterEach
    open fun teardown() {
        pkgs.clear()
    }

    suspend fun CSIProcessor.assertJurisdiction(type: DataArea.Type) {
        DataArea.Type.values().forEach {
            if (it == type) {
                hasJurisdiction(it) shouldBe true
            } else {
                hasJurisdiction(it) shouldBe false
            }
        }
    }

    open fun mockPkg(
        pkgId: Pkg.Id,
        source: LocalPath? = null,
        userHandle: UserHandle2 = UserHandle2(0),
    ): Installed {
        val mockPkg = mockk<Installed>().apply {
            every { id } returns pkgId
            every { sourceDir } returns source
            every { packageInfo } returns mockk()
        }
        coEvery { pkgRepo.query(pkgId, UserHandle2(-1)) } returns setOf(mockPkg)
        coEvery { pkgRepo.query(pkgId, userHandle) } returns setOf(mockPkg)

        return mockPkg.also {
            pkgs.add(it)
        }
    }

    open fun mockMarker(pkgId: Pkg.Id, location: DataArea.Type, prefixFree: String) {

        val marker = mockk<Marker>().apply {
            every { areaType } returns location
            every { segments } returns prefixFree.split("/")
            every { flags } returns emptySet()
        }

        val match = mockk<Marker.Match>().apply {
            every { packageNames } returns setOf(pkgId)
            every { flags } returns emptySet()
        }
        every { marker.match(location, prefixFree.split("/")) } returns match

        clutterRepo.apply {
            coEvery { getMarkerForPkg(pkgId) } returns setOf(marker)
            coEvery { getMarkerForLocation(location) } returns setOf(marker)
            coEvery { match(location, prefixFree.split("/")) } returns setOf(match)
        }
    }

    @Test abstract fun `test jurisdiction`()

    @Test abstract fun `determine area successfully`()

    @Test abstract fun `fail to determine area`()

}