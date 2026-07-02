package eu.darken.sdmse.common.forensics

import android.content.Context
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.sharedresource.Resource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import javax.inject.Provider

class FileForensicsTest : BaseTest() {

    @MockK lateinit var context: Context
    @MockK lateinit var pkgRepo: PkgRepo
    @MockK lateinit var csiProcessor: CSIProcessor
    @MockK lateinit var testAreaInfo: AreaInfo
    @MockK lateinit var gatewaySwitch: GatewaySwitch
    @MockK lateinit var pkgOps: PkgOps
    @MockK lateinit var shellOps: ShellOps

    val processors = mutableSetOf<CSIProcessor>()
    private val processorsProvider = Provider<Set<CSIProcessor>> { processors }

    @BeforeEach fun setup() {
        MockKAnnotations.init(this)

        coEvery { csiProcessor.identifyArea(any()) } returns testAreaInfo
        processors.add(csiProcessor)

        every { gatewaySwitch.sharedResource } returns mockk<SharedResource<Any>>().apply {
            every { resourceId } returns "gateway:SR"
            coEvery { get() } returns mockk<Resource<Any>>().apply {
                every { close() } returns Unit
            }
            every { isClosed } returns true
            every { close() } returns Unit
        }
        every { pkgOps.sharedResource } returns mockk<SharedResource<Any>>().apply {
            every { resourceId } returns "pkgops:SR"
            coEvery { get() } returns mockk<Resource<Any>>().apply {
                every { close() } returns Unit
            }
            every { isClosed } returns true
            every { close() } returns Unit
        }
        every { shellOps.sharedResource } returns mockk<SharedResource<Any>>().apply {
            every { resourceId } returns "shellops:SR"
            coEvery { get() } returns mockk<Resource<Any>>().apply {
                every { close() } returns Unit
            }
            every { isClosed } returns true
            every { close() } returns Unit
        }
    }

    @AfterEach fun teardown() {

    }

    @Test fun init() = runTest2(autoCancel = true) {
        val forensics = FileForensics(this, context, pkgRepo, processorsProvider, gatewaySwitch, pkgOps)
        val testPath = LocalPath.build("/test")
        val areaInfo = forensics.identifyArea(testPath)
        areaInfo shouldBe testAreaInfo
    }

    @Test fun `installed-owner check uses each owner's own user, not the area's`() = runTest2(autoCancel = true) {
        val forensics = FileForensics(this, context, pkgRepo, processorsProvider, gatewaySwitch, pkgOps)

        val pkgId = "com.some.app".toPkgId()
        val ownerUser = UserHandle2(10)
        val areaUser = UserHandle2(0)

        every { testAreaInfo.type } returns DataArea.Type.PRIVATE_DATA
        every { testAreaInfo.userHandle } returns areaUser
        every { testAreaInfo.isBlackListLocation } returns true
        every { testAreaInfo.file } returns LocalPath.build("/data/user/0/com.some.app")

        coEvery { csiProcessor.hasJurisdiction(DataArea.Type.PRIVATE_DATA) } returns true
        coEvery { csiProcessor.findOwners(testAreaInfo) } returns CSIProcessor.Result(
            owners = setOf(Owner(pkgId, ownerUser)),
        )

        // Installed for the owner's own user (10), but not for the area's user (0). The corpse check must
        // honor the owner's user, otherwise this live app's data is misclassified as a corpse.
        coEvery { pkgRepo.query(any(), any()) } returns emptySet()
        coEvery { pkgRepo.query(pkgId, ownerUser) } returns setOf(mockk<Installed>())

        forensics.findOwners(testAreaInfo).apply {
            installedOwners.map { it.pkgId } shouldBe listOf(pkgId)
            isCorpse shouldBe false
        }
    }
}