package eu.darken.sdmse.setup.inventory

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PermissionInfo
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.setup.SetupModule
import eu.darken.sdmse.setup.inventory.InventorySetupModule.InventoryAccess
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class InventorySetupModuleTest : BaseTest() {

    private lateinit var pkgOps: PkgOps

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        pkgOps = mockk(relaxed = true)
        // GET_INSTALLED_APPS is a non-AOSP permission and reports itself as granted where it does not
        // exist, granting it too keeps this independent of whether Robolectric knows the permission.
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(
            Permission.GET_INSTALLED_APPS.permissionId,
            Permission.QUERY_ALL_PACKAGES.permissionId,
        )
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `missing permissions leave the list unchecked and skip the query`() = runTest {
        // Only a permission that exists can be missing: the module's own check treats an unknown
        // permission id as granted.
        Shadows.shadowOf(context.packageManager).addPermissionInfo(
            PermissionInfo().apply {
                name = Permission.GET_INSTALLED_APPS.permissionId
                packageName = context.packageName
            }
        )
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .denyPermissions(Permission.GET_INSTALLED_APPS.permissionId)

        val result = newModule(backgroundScope).awaitResult()

        result.access shouldBe InventoryAccess.NotChecked
        result.isComplete shouldBe false
        coVerify(exactly = 0) { pkgOps.queryPkgs(any(), any(), any()) }
    }

    @Test
    fun `a credible list is valid and completes setup`() = runTest {
        coEvery { pkgOps.queryPkgs(any(), any(), any()) } returns pkgs(context.packageName, "android")

        val result = newModule(backgroundScope).awaitResult()

        result.access shouldBe InventoryAccess.Valid
        result.isComplete shouldBe true
    }

    @Test
    fun `an empty list is incomplete`() = runTest {
        coEvery { pkgOps.queryPkgs(any(), any(), any()) } returns pkgs()

        val result = newModule(backgroundScope).awaitResult()

        result.access shouldBe InventoryAccess.Incomplete
        result.isComplete shouldBe false
    }

    @Test
    fun `a list without our own package is incomplete`() = runTest {
        coEvery { pkgOps.queryPkgs(any(), any(), any()) } returns pkgs("android", "com.example.other")

        val result = newModule(backgroundScope).awaitResult()

        result.access shouldBe InventoryAccess.Incomplete
    }

    @Test
    fun `a list without any core package is incomplete`() = runTest {
        coEvery { pkgOps.queryPkgs(any(), any(), any()) } returns pkgs(context.packageName, "com.example.other")

        val result = newModule(backgroundScope).awaitResult()

        result.access shouldBe InventoryAccess.Incomplete
    }

    @Test
    fun `a failing query is a probe failure and does not complete setup`() = runTest {
        coEvery { pkgOps.queryPkgs(any(), any(), any()) } throws SecurityException("Nope")

        val result = newModule(backgroundScope).awaitResult()

        result.access shouldBe InventoryAccess.ProbeFailed
        result.isComplete shouldBe false
    }

    @Test
    fun `a refresh after a failed probe recovers`() = runTest {
        coEvery { pkgOps.queryPkgs(any(), any(), any()) } throws IllegalStateException("Nope")
        val module = newModule(backgroundScope)
        val seen = mutableListOf<SetupModule.State>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { module.state.toList(seen) }
        advanceUntilIdle()

        seen.results().last().access shouldBe InventoryAccess.ProbeFailed

        coEvery { pkgOps.queryPkgs(any(), any(), any()) } returns pkgs(context.packageName, "android")
        module.refresh()
        advanceUntilIdle()

        val recovered = seen.results().last()
        recovered.access shouldBe InventoryAccess.Valid
        recovered.isComplete shouldBe true
    }

    @Test
    fun `every trigger emits loading before the settled result`() = runTest {
        coEvery { pkgOps.queryPkgs(any(), any(), any()) } returns pkgs(context.packageName, "android")
        val module = newModule(backgroundScope)
        val seen = mutableListOf<SetupModule.State>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { module.state.toList(seen) }
        advanceUntilIdle()

        module.refresh()
        advanceUntilIdle()

        seen.map { it::class } shouldBe listOf(
            InventorySetupModule.Loading::class,
            InventorySetupModule.Result::class,
            InventorySetupModule.Loading::class,
            InventorySetupModule.Result::class,
        )
    }

    @Test
    fun `a probe cancelled by a refresh does not surface as a failure`() = runTest {
        coEvery { pkgOps.queryPkgs(any(), any(), any()) } coAnswers { awaitCancellation() }
        val module = newModule(backgroundScope)
        val seen = mutableListOf<SetupModule.State>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { module.state.toList(seen) }
        advanceUntilIdle()

        coEvery { pkgOps.queryPkgs(any(), any(), any()) } returns pkgs(context.packageName, "android")
        module.refresh()
        advanceUntilIdle()

        seen.results().none { it.access is InventoryAccess.ProbeFailed } shouldBe true
        seen.results().last().access shouldBe InventoryAccess.Valid
    }

    private fun newModule(appScope: CoroutineScope) = InventorySetupModule(
        appScope = appScope,
        context = context,
        pkgOps = pkgOps,
    )

    private suspend fun InventorySetupModule.awaitResult(): InventorySetupModule.Result =
        state.filterIsInstance<InventorySetupModule.Result>().first()

    private fun List<SetupModule.State>.results(): List<InventorySetupModule.Result> =
        filterIsInstance<InventorySetupModule.Result>()

    private fun pkgs(vararg names: String): Collection<PackageInfo> = names.map { name ->
        PackageInfo().apply { packageName = name }
    }
}
