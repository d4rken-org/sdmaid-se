package eu.darken.sdmse.appcontrol.core

import eu.darken.sdmse.appcontrol.core.archive.ArchiveSupport
import eu.darken.sdmse.appcontrol.core.usage.UsageTool
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

/**
 * Robolectric is mandatory: [PkgOps.querySizeStats] defaults its storage UUID to
 * `StorageManager.UUID_DEFAULT`, which is null against the plain android.jar stub.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AppScanTest : BaseTest() {

    // AppScan owns a SharedResource.createKeepAlive(...) and adopts pkgOps' resource as a child,
    // so both need a scope that outlives the individual test coroutine. Mirrors AppControlTest.
    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun cleanup() {
        keepAliveScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        unmockkAll()
    }

    private val user = UserHandle2(handleId = 0)

    private fun pkg(pkgName: String): Installed {
        val pkgId = Pkg.Id(pkgName)
        return mockk<Installed>(relaxed = true).apply {
            every { id } returns pkgId
            every { packageName } returns pkgName
            every { userHandle } returns user
            every { installId } returns InstallId(pkgId, user)
        }
    }

    private class Harness(
        val appScan: AppScan,
        val pkgOps: PkgOps,
        val repoData: MutableStateFlow<PkgRepo.PkgData>,
    )

    private fun harness(pkgs: Collection<Installed>): Harness {
        val repoData = MutableStateFlow(PkgRepo.PkgData.from(pkgs))
        val pkgRepo = mockk<PkgRepo>().apply {
            every { data } returns repoData
        }
        val pkgOps = mockk<PkgOps>().apply {
            every { sharedResource } returns SharedResource.createKeepAlive("pkgOps", keepAliveScope)
            coEvery { querySizeStats(any(), any()) } returns null
        }
        val userManager = mockk<UserManager2>().apply {
            coEvery { allUsers() } returns setOf(UserProfile2(handle = user))
        }
        val appScan = AppScan(
            appScope = keepAliveScope,
            dispatcherProvider = TestDispatcherProvider(),
            pkgRepo = pkgRepo,
            pkgOps = pkgOps,
            usageTool = mockk<UsageTool>(relaxed = true),
            userManager = userManager,
            archiveSupport = mockk<ArchiveSupport>(relaxed = true),
        )
        return Harness(appScan = appScan, pkgOps = pkgOps, repoData = repoData)
    }

    @Test
    fun `a size the bulk prefetch stored as null is not queried again`() = runTest2 {
        // The prefetch stores `id -> null` for a failed query. Reading that back as "no entry" makes
        // every single-package lookup re-run the query that already failed.
        val target = pkg("eu.thlab.target")
        val targetId = target.installId
        val harness = harness(listOf(target))

        harness.appScan.allApps(user = null, includeUsage = false, includeActive = false, includeSize = true)
        harness.appScan.app(
            pkgId = Pkg.Id("eu.thlab.target"),
            user = null,
            includeUsage = false,
            includeActive = false,
            includeSize = true,
        )

        coVerify(exactly = 1) { harness.pkgOps.querySizeStats(targetId, any()) }
    }

    @Test
    fun `a size that the prefetch never saw is queried on the single package path`() = runTest2 {
        val known = pkg("eu.thlab.known")
        val late = pkg("eu.thlab.late")
        val lateId = late.installId
        val harness = harness(listOf(known))

        harness.appScan.allApps(user = null, includeUsage = false, includeActive = false, includeSize = true)

        harness.repoData.value = PkgRepo.PkgData.from(listOf(known, late))
        harness.appScan.app(
            pkgId = Pkg.Id("eu.thlab.late"),
            user = null,
            includeUsage = false,
            includeActive = false,
            includeSize = true,
        )

        coVerify(exactly = 1) { harness.pkgOps.querySizeStats(lateId, any()) }
    }
}
