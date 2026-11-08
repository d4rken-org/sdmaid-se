package eu.darken.sdmse.appcontrol.core.archive

import android.content.Context
import android.content.pm.PackageManager
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.pkgops.PkgOpsException
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class ArchiveSupportTest : BaseTest() {

    @MockK lateinit var context: Context
    @MockK lateinit var packageManager: PackageManager
    @MockK lateinit var userManager2: UserManager2
    @MockK lateinit var pkgOps: PkgOps

    private val currentUser = UserHandle2(handleId = 0)
    private val otherUser = UserHandle2(handleId = 10)

    private lateinit var archiveSupport: ArchiveSupport

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(BuildWrap)
        mockkObject(BuildWrap.VERSION)

        every { context.packageManager } returns packageManager
        coEvery { userManager2.currentUser() } returns UserProfile2(handle = currentUser)

        archiveSupport = ArchiveSupport(context, userManager2, pkgOps)
    }

    @AfterEach
    fun teardown() {
        unmockkObject(BuildWrap)
        unmockkObject(BuildWrap.VERSION)
    }

    private fun enableArchiving() {
        every { BuildWrap.VERSION.SDK_INT } returns 35
        every { BuildWrap.VERSION.CODENAME } returns "REL"
    }

    private fun installed(
        pkgName: String,
        user: UserHandle2,
        isSystem: Boolean = false,
    ): Installed = mockk<Installed>(moreInterfaces = arrayOf(InstallDetails::class)).apply {
        val pkgId = Pkg.Id(pkgName)
        every { id } returns pkgId
        every { packageName } returns pkgName
        every { userHandle } returns user
        every { installId } returns InstallId(pkgId, user)
        every { (this@apply as InstallDetails).isSystemApp } returns isSystem
        every { (this@apply as InstallDetails).isUpdatedSystemApp } returns false
        every { (this@apply as InstallDetails).installerInfo } returns InstallerInfo()
    }

    private class Supplier(private val pkgs: Set<String>) {
        val calls = mutableListOf<UserHandle2>()
        val fn: suspend (UserHandle2) -> Set<String> = { user ->
            calls += user
            pkgs
        }
    }

    @Test
    fun `returns false when API level is below 35`() {
        every { BuildWrap.VERSION.SDK_INT } returns 34
        every { BuildWrap.VERSION.CODENAME } returns "REL"

        archiveSupport.isArchivingEnabled shouldBe false
    }

    @Test
    fun `returns true when API is 35 and Flags class not found`() {
        enableArchiving()

        // Since Flags class won't exist in test environment, assume archiving is available on API 35+
        archiveSupport.isArchivingEnabled shouldBe true
    }

    @Test
    fun `handles VanillaIceCream codename for API 35`() {
        every { BuildWrap.VERSION.SDK_INT } returns 34
        every { BuildWrap.VERSION.CODENAME } returns "VanillaIceCream"

        // Even with the codename, Flags class won't exist - assume archiving is available on API 35+
        archiveSupport.isArchivingEnabled shouldBe true
    }

    @Test
    fun `current user app follows isAppArchivable`() = runTest2 {
        enableArchiving()
        val supplier = Supplier(setOf("eu.thlab.target"))

        every { packageManager.isAppArchivable("eu.thlab.target") } returns true
        archiveSupport.isArchivable(installed("eu.thlab.target", currentUser), supplier.fn) shouldBe true

        every { packageManager.isAppArchivable("eu.thlab.target") } returns false
        archiveSupport.isArchivable(installed("eu.thlab.target", currentUser), supplier.fn) shouldBe false

        supplier.calls shouldBe emptyList()
    }

    @Test
    fun `current user app is not archivable when isAppArchivable throws`() = runTest2 {
        enableArchiving()
        every { packageManager.isAppArchivable(any()) } throws PackageManager.NameNotFoundException()

        archiveSupport.isArchivable(installed("eu.thlab.target", currentUser), Supplier(emptySet()).fn) shouldBe false
    }

    @Test
    fun `other user app is archivable only when in the user's archivable set`() = runTest2 {
        enableArchiving()
        val supplier = Supplier(setOf("eu.thlab.target"))

        archiveSupport.isArchivable(installed("eu.thlab.target", otherUser), supplier.fn) shouldBe true
        archiveSupport.isArchivable(installed("eu.thlab.other", otherUser), supplier.fn) shouldBe false

        supplier.calls shouldBe listOf(otherUser, otherUser)
        verify(exactly = 0) { packageManager.isAppArchivable(any()) }
    }

    @Test
    fun `system app is not archivable and skips both lookups`() = runTest2 {
        enableArchiving()
        val supplier = Supplier(setOf("eu.thlab.target"))

        archiveSupport.isArchivable(
            installed("eu.thlab.target", currentUser, isSystem = true),
            supplier.fn,
        ) shouldBe false
        archiveSupport.isArchivable(
            installed("eu.thlab.target", otherUser, isSystem = true),
            supplier.fn,
        ) shouldBe false

        supplier.calls shouldBe emptyList()
        verify(exactly = 0) { packageManager.isAppArchivable(any()) }
    }

    @Test
    fun `archivable package lookup failure yields an empty set`() = runTest2 {
        coEvery { pkgOps.getArchivablePackages(otherUser, any()) } throws PkgOpsException("Mode AUTO is unavailable")

        archiveSupport.getArchivablePackages(otherUser) shouldBe emptySet()
    }

    @Test
    fun `archivable package lookup passes the result through`() = runTest2 {
        coEvery { pkgOps.getArchivablePackages(otherUser, any()) } returns setOf("eu.thlab.target")

        archiveSupport.getArchivablePackages(otherUser) shouldBe setOf("eu.thlab.target")
    }
}
