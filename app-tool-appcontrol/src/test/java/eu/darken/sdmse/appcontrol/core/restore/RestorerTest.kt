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
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeout
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
    private val secondaryUserHandle = UserHandle2(handleId = 10)

    private fun archivedApp(pkgName: String, forUser: UserHandle2 = systemUserHandle): AppInfo {
        val pkgId = Pkg.Id(pkgName)
        // isArchived is a type check against ArchivedPkg, so the mock has to be of that type.
        val pkg = mockk<ArchivedPkg>().apply {
            every { id } returns pkgId
            every { packageName } returns pkgName
            every { label } returns pkgName.toCaString()
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
            canBeArchived = false,
            canBeRestored = true,
        )
    }

    private fun restoredPkg(pkgName: String, forUser: UserHandle2 = systemUserHandle): Installed {
        val pkgId = Pkg.Id(pkgName)
        return mockk<Installed>().apply {
            every { id } returns pkgId
            every { packageName } returns pkgName
            every { userHandle } returns forUser
            every { installId } returns InstallId(pkgId, forUser)
        }
    }

    private class Setup(
        val restorer: Restorer,
        val automation: AutomationSubmitter,
        val unarchiveManager: UnarchiveManager,
        val pkgRepo: PkgRepo,
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
                unarchiveError != null -> coEvery { requestUnarchive(any<InstallId>(), any()) } throws unarchiveError
                unarchiveResult != null -> coEvery { requestUnarchive(any<InstallId>(), any()) } returns unarchiveResult
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
        return Setup(
            restorer = restorer,
            automation = automation,
            unarchiveManager = unarchiveManager,
            pkgRepo = pkgRepo,
        )
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

    // TimeoutCancellationException's constructor is internal, so take one from a real timeout.
    private suspend fun statusTimeout(): TimeoutCancellationException =
        runCatching { withTimeout(1) { awaitCancellation() } }.exceptionOrNull() as TimeoutCancellationException

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
        coVerify(exactly = 0) { setup.unarchiveManager.requestUnarchive(any<InstallId>(), any()) }
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
        coVerify(exactly = 0) { setup.unarchiveManager.requestUnarchive(any<InstallId>(), any()) }
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

    @Test
    fun `a restore for the current user waits reactively`() = runTest2 {
        val target = archivedApp("eu.thlab.target", forUser = systemUserHandle)
        val setup = setupRestorer(
            unarchiveResult = unarchiveSuccess(),
            restoredPkgs = setOf(restoredPkg("eu.thlab.target", forUser = systemUserHandle)),
        )

        setup.restorer.restore(target)

        // refresh() only happens inside the polling branch
        coVerify(exactly = 0) { setup.pkgRepo.refresh() }
    }

    @Test
    fun `a restore for another user polls`() = runTest2 {
        val target = archivedApp("eu.thlab.target", forUser = secondaryUserHandle)
        val setup = setupRestorer(
            unarchiveResult = unarchiveSuccess(),
            restoredPkgs = setOf(restoredPkg("eu.thlab.target", forUser = secondaryUserHandle)),
        )

        setup.restorer.restore(target)

        coVerify(atLeast = 1) { setup.pkgRepo.refresh() }
    }

    @Test
    fun `a restore for another user requests the unarchive for that user's install`() = runTest2 {
        val target = archivedApp("eu.thlab.target", forUser = secondaryUserHandle)
        val setup = setupRestorer(
            unarchiveResult = unarchiveSuccess(),
            restoredPkgs = setOf(restoredPkg("eu.thlab.target", forUser = secondaryUserHandle)),
        )

        setup.restorer.restore(target)

        coVerify(exactly = 1) {
            setup.unarchiveManager.requestUnarchive(InstallId(Pkg.Id("eu.thlab.target"), secondaryUserHandle), any())
        }
    }

    @Test
    fun `another user's restore keeps polling when the unarchive status times out`() = runTest2 {
        val target = archivedApp("eu.thlab.target", forUser = secondaryUserHandle)
        val restored = restoredPkg("eu.thlab.target", forUser = secondaryUserHandle)
        val setup = setupRestorer(unarchiveError = statusTimeout())
        every { setup.pkgRepo.data } returnsMany listOf(
            flowOf(PkgRepo.PkgData.from(setOf(target.pkg))),
            flowOf(PkgRepo.PkgData.from(setOf(restored))),
        )

        setup.restorer.restore(target)

        coVerify(exactly = 2) { setup.pkgRepo.refresh() }
        coVerify(exactly = 0) { setup.automation.submit(any()) }
    }

    @Test
    fun `another user's restore fails without automation when the unarchive request throws`() = runTest2 {
        val target = archivedApp("eu.thlab.target", forUser = secondaryUserHandle)
        val error = IllegalStateException("PackageInstaller exploded")
        val setup = setupRestorer(unarchiveError = error)

        val thrown = shouldThrow<RestoreException> { setup.restorer.restore(target) }

        thrown.cause shouldBeSameInstanceAs error
        coVerify(exactly = 0) { setup.automation.submit(any()) }
        coVerify(exactly = 0) { setup.pkgRepo.refresh() }
    }

    @Test
    fun `another user's restore fails without automation when the unarchive request fails`() = runTest2 {
        val target = archivedApp("eu.thlab.target", forUser = secondaryUserHandle)
        val setup = setupRestorer(unarchiveResult = unarchiveFailure())

        val thrown = shouldThrow<RestoreException> { setup.restorer.restore(target) }

        thrown.message shouldContain "Nope"
        thrown.message shouldContain "status=${PackageInstaller.STATUS_FAILURE}"
        coVerify(exactly = 0) { setup.automation.submit(any()) }
        coVerify(exactly = 0) { setup.pkgRepo.refresh() }
    }

    @Test
    fun `another user's restore is not completed by a restored copy in a different user`() = runTest2 {
        val target = archivedApp("eu.thlab.target", forUser = secondaryUserHandle)
        val setup = setupRestorer(
            unarchiveResult = unarchiveSuccess(),
            restoredPkgs = setOf(restoredPkg("eu.thlab.target", forUser = systemUserHandle)),
        )

        val thrown = shouldThrow<RestoreException> { setup.restorer.restore(target) }

        thrown.cause.shouldBeInstanceOf<TimeoutCancellationException>()
        coVerify(atLeast = 2) { setup.pkgRepo.refresh() }
        coVerify(exactly = 0) { setup.automation.submit(any()) }
    }

    @Test
    fun `a disabled restore button fails without waiting for the restore`() = runTest2 {
        val target = archivedApp("eu.thlab.target", forUser = systemUserHandle)
        val unavailable = RestoreUnavailableException("Restore button is disabled for ${target.installId}")
        val setup = setupRestorer(
            useRoot = false,
            useAdb = false,
            automationResult = RestoreAutomationTask.Result(
                successful = emptyList(),
                failed = mapOf(target.installId to unavailable),
            ),
        )

        val thrown = shouldThrow<RestoreException> { setup.restorer.restore(target) }

        thrown.cause shouldBeSameInstanceAs unavailable
        coVerify(exactly = 1) { setup.automation.submit(any()) }
        verify(exactly = 0) { setup.pkgRepo.data }
        coVerify(exactly = 0) { setup.pkgRepo.refresh() }
    }
}
