package eu.darken.sdmse.appcontrol.core.archive

import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.automation.core.AutomationSubmitter
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.container.ArchivedPkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import eu.darken.sdmse.setup.SetupModule
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class ArchiverTest : BaseTest() {

    // Archiver creates a keep-alive SharedResource on the injected scope.
    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @AfterEach
    fun teardown() {
        keepAliveScope.coroutineContext[Job]?.cancel()
    }

    private val systemUserHandle = UserHandle2(handleId = 0)
    private val secondaryUserHandle = UserHandle2(handleId = 10)

    private fun installedApp(pkgName: String, forUser: UserHandle2 = systemUserHandle): AppInfo {
        val pkgId = Pkg.Id(pkgName)
        val pkg = mockk<Installed>().apply {
            every { id } returns pkgId
            every { packageName } returns pkgName
            every { userHandle } returns forUser
            every { installId } returns InstallId(pkgId, forUser)
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
            canBeArchived = true,
            canBeRestored = false,
        )
    }

    private fun archivedPkg(pkgName: String, forUser: UserHandle2 = systemUserHandle): Installed {
        val pkgId = Pkg.Id(pkgName)
        // isArchived is a type check against ArchivedPkg, so the mock has to be of that type.
        return mockk<ArchivedPkg>().apply {
            every { id } returns pkgId
            every { packageName } returns pkgName
            every { label } returns pkgName.toCaString()
            every { userHandle } returns forUser
            every { installId } returns InstallId(pkgId, forUser)
        }
    }

    private class Setup(
        val archiver: Archiver,
        val automation: AutomationSubmitter,
        val pkgRepo: PkgRepo,
    )

    private fun setupArchiver(
        automationResult: ArchiveAutomationTask.Result = ArchiveAutomationTask.Result(
            successful = emptySet(),
            failed = emptyMap(),
        ),
        archivedPkgs: Collection<Installed> = emptySet(),
    ): Setup {
        val pkgRepo = mockk<PkgRepo>().apply {
            every { data } returns flowOf(PkgRepo.PkgData.from(archivedPkgs))
            coEvery { refresh() } returns archivedPkgs
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
                    override val isComplete: Boolean = true
                },
            )
            coJustRun { refresh() }
        }
        val archiveSupport = mockk<ArchiveSupport>().apply {
            every { isArchivingEnabled } returns true
        }
        val rootManager = mockk<RootManager>().apply {
            every { useRoot } returns flowOf(false)
        }
        val adbManager = mockk<AdbManager>().apply {
            every { useAdb } returns flowOf(false)
        }

        val archiver = Archiver(
            appScope = keepAliveScope,
            dispatcherProvider = TestDispatcherProvider(),
            pkgRepo = pkgRepo,
            shellOps = mockk<ShellOps>(),
            rootManager = rootManager,
            adbManager = adbManager,
            userManager2 = userManager2,
            archiveSupport = archiveSupport,
            automation = automation,
            automationSetupModule = automationSetupModule,
        )
        return Setup(
            archiver = archiver,
            automation = automation,
            pkgRepo = pkgRepo,
        )
    }

    @Test
    fun `an archive for the current user waits reactively`() = runTest2 {
        val target = installedApp("eu.thlab.target", forUser = systemUserHandle)
        val setup = setupArchiver(
            archivedPkgs = setOf(archivedPkg("eu.thlab.target", forUser = systemUserHandle)),
        )

        setup.archiver.archive(target)

        // refresh() only happens inside the polling branch
        coVerify(exactly = 0) { setup.pkgRepo.refresh() }
    }

    @Test
    fun `an archive for another user polls`() = runTest2 {
        val target = installedApp("eu.thlab.target", forUser = secondaryUserHandle)
        val setup = setupArchiver(
            archivedPkgs = setOf(archivedPkg("eu.thlab.target", forUser = secondaryUserHandle)),
        )

        setup.archiver.archive(target)

        coVerify(atLeast = 1) { setup.pkgRepo.refresh() }
    }
}
