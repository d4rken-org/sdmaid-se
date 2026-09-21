package eu.darken.sdmse.appcontrol.core.restore

import android.content.pm.PackageInstaller
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.automation.core.AutomationSubmitter
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.container.ArchivedPkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import eu.darken.sdmse.setup.SetupModule
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class RestorerTest : BaseTest() {

    // Restorer creates a keep-alive SharedResource on the injected scope.
    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @BeforeEach
    fun setup() {
        mockkObject(BuildWrap)
        mockkObject(BuildWrap.VERSION)
    }

    @AfterEach
    fun teardown() {
        unmockkObject(BuildWrap.VERSION)
        unmockkObject(BuildWrap)
        keepAliveScope.coroutineContext[Job]?.cancel()
    }

    private val systemUserHandle = UserHandle2(handleId = 0)

    private fun archivedApp(pkgName: String): AppInfo {
        val pkgId = Pkg.Id(pkgName)
        // isArchived is a type check against ArchivedPkg, so the mock has to be of that type.
        val pkg = mockk<ArchivedPkg>().apply {
            every { id } returns pkgId
            every { packageName } returns pkgName
            every { label } returns pkgName.toCaString()
            every { userHandle } returns systemUserHandle
            every { installId } returns InstallId(pkgId, systemUserHandle)
        }
        return AppInfo(
            pkg = pkg,
            isActive = null,
            sizes = null,
            usage = null,
            userProfile = null,
            canBeToggled = false,
            canBeStopped = false,
            canBeExported = false,
            canBeDeleted = false,
            canBeArchived = false,
            canBeRestored = true,
        )
    }

    private fun restoredPkg(pkgName: String): Installed {
        val pkgId = Pkg.Id(pkgName)
        return mockk<Installed>().apply {
            every { id } returns pkgId
            every { packageName } returns pkgName
            every { userHandle } returns systemUserHandle
            every { installId } returns InstallId(pkgId, systemUserHandle)
        }
    }

    private class Setup(
        val restorer: Restorer,
        val automation: AutomationSubmitter,
        val unarchiveManager: UnarchiveManager,
    )

    private fun setupRestorer(
        apiLevel: Int = 35,
        useRoot: Boolean = true,
        useAdb: Boolean = false,
        automationComplete: Boolean = true,
        automationResult: RestoreAutomationTask.Result = RestoreAutomationTask.Result(
            successful = emptySet(),
            failed = emptyMap(),
        ),
        unarchiveResult: UnarchiveManager.UnarchiveResult? = null,
        unarchiveError: Exception? = null,
        restoredPkgs: Collection<Installed> = emptySet(),
    ): Setup {
        every { BuildWrap.VERSION.SDK_INT } returns apiLevel
        every { BuildWrap.VERSION.CODENAME } returns "REL"

        val pkgRepo = mockk<PkgRepo>().apply {
            every { data } returns flowOf(PkgRepo.PkgData.from(restoredPkgs))
            coEvery { refresh() } returns restoredPkgs
        }
        val userManager2 = mockk<UserManager2>().apply {
            coEvery { currentUser() } returns UserProfile2(handle = systemUserHandle)
        }
        val automation = mockk<AutomationSubmitter>().apply {
            coEvery { submit(any()) } returns automationResult
        }
        val automationSetupModule = mockk<SetupModule>().apply {
            every { state } returns flowOf(
                object : SetupModule.State.Current {
                    override val type = SetupModule.Type.AUTOMATION
                    override val isComplete: Boolean = automationComplete
                },
            )
            coJustRun { refresh() }
        }
        val unarchiveManager = mockk<UnarchiveManager>().apply {
            when {
                unarchiveError != null -> coEvery { requestUnarchive(any(), any()) } throws unarchiveError
                unarchiveResult != null -> coEvery { requestUnarchive(any(), any()) } returns unarchiveResult
            }
        }
        val rootManager = mockk<RootManager>().apply {
            every { this@apply.useRoot } returns flowOf(useRoot)
        }
        val adbManager = mockk<AdbManager>().apply {
            every { this@apply.useAdb } returns flowOf(useAdb)
        }

        val restorer = Restorer(
            appScope = keepAliveScope,
            dispatcherProvider = TestDispatcherProvider(),
            pkgRepo = pkgRepo,
            userManager2 = userManager2,
            automation = automation,
            automationSetupModule = automationSetupModule,
            unarchiveManager = unarchiveManager,
            rootManager = rootManager,
            adbManager = adbManager,
        )
        return Setup(restorer = restorer, automation = automation, unarchiveManager = unarchiveManager)
    }

    private fun unarchiveFailure() = UnarchiveManager.UnarchiveResult(
        packageName = "eu.thlab.target",
        status = PackageInstaller.STATUS_FAILURE,
        statusMessage = "Nope",
    )

    private fun unarchiveSuccess() = UnarchiveManager.UnarchiveResult(
        packageName = "eu.thlab.target",
        status = PackageInstaller.STATUS_SUCCESS,
        statusMessage = null,
    )

    private fun automationFailure(target: AppInfo) = RestoreAutomationTask.Result(
        successful = emptySet(),
        failed = mapOf(target.installId to IllegalStateException("Automation failed")),
    )

    private fun automationSuccess(target: AppInfo) = RestoreAutomationTask.Result(
        successful = setOf(target.installId),
        failed = emptyMap(),
    )

    @Test
    fun `a failed unarchive request runs the automation fallback only once`() = runTest2 {
        val target = archivedApp("eu.thlab.target")
        val setup = setupRestorer(
            unarchiveResult = unarchiveFailure(),
            automationResult = automationFailure(target),
        )

        shouldThrow<RestoreException> { setup.restorer.restore(target) }

        coVerify(exactly = 1) { setup.automation.submit(any()) }
    }

    @Test
    fun `an unarchive request that throws runs the automation fallback only once`() = runTest2 {
        val target = archivedApp("eu.thlab.target")
        val setup = setupRestorer(
            unarchiveError = IllegalStateException("PackageInstaller exploded"),
            automationResult = automationFailure(target),
        )

        shouldThrow<RestoreException> { setup.restorer.restore(target) }

        coVerify(exactly = 1) { setup.automation.submit(any()) }
    }

    @Test
    fun `a failed unarchive request falls back to a successful automation restore`() = runTest2 {
        val target = archivedApp("eu.thlab.target")
        val setup = setupRestorer(
            unarchiveResult = unarchiveFailure(),
            automationResult = automationSuccess(target),
            restoredPkgs = setOf(restoredPkg("eu.thlab.target")),
        )

        setup.restorer.restore(target)

        coVerify(exactly = 1) { setup.automation.submit(any()) }
    }

    @Test
    fun `below API 35 the automation fallback is used once`() = runTest2 {
        val target = archivedApp("eu.thlab.target")
        val setup = setupRestorer(
            apiLevel = 34,
            automationResult = automationSuccess(target),
            restoredPkgs = setOf(restoredPkg("eu.thlab.target")),
        )

        setup.restorer.restore(target)

        coVerify(exactly = 1) { setup.automation.submit(any()) }
        coVerify(exactly = 0) { setup.unarchiveManager.requestUnarchive(any(), any()) }
    }

    @Test
    fun `without root or ADB the automation fallback is used once`() = runTest2 {
        val target = archivedApp("eu.thlab.target")
        val setup = setupRestorer(
            useRoot = false,
            useAdb = false,
            automationResult = automationSuccess(target),
            restoredPkgs = setOf(restoredPkg("eu.thlab.target")),
        )

        setup.restorer.restore(target)

        coVerify(exactly = 1) { setup.automation.submit(any()) }
        coVerify(exactly = 0) { setup.unarchiveManager.requestUnarchive(any(), any()) }
    }

    @Test
    fun `a successful unarchive request skips the automation fallback`() = runTest2 {
        val target = archivedApp("eu.thlab.target")
        val setup = setupRestorer(
            unarchiveResult = unarchiveSuccess(),
            restoredPkgs = setOf(restoredPkg("eu.thlab.target")),
        )

        setup.restorer.restore(target)

        coVerify(exactly = 0) { setup.automation.submit(any()) }
    }
}
