package eu.darken.sdmse.appcontrol.core

import android.content.Context
import eu.darken.sdmse.appcontrol.core.archive.ArchiveSupport
import eu.darken.sdmse.appcontrol.core.usage.UsageTool
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.container.NormalPkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.features.InstallerInfo
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
    private val otherUser = UserHandle2(handleId = 10)
    private val thirdUser = UserHandle2(handleId = 11)

    private fun pkg(pkgName: String): Installed {
        val pkgId = Pkg.Id(pkgName)
        return mockk<Installed>(relaxed = true).apply {
            every { id } returns pkgId
            every { packageName } returns pkgName
            every { userHandle } returns user
            every { installId } returns InstallId(pkgId, user)
        }
    }

    private fun normalPkg(pkgName: String, handle: UserHandle2): NormalPkg {
        val pkgId = Pkg.Id(pkgName)
        return mockk<NormalPkg>(relaxed = true).apply {
            every { id } returns pkgId
            every { packageName } returns pkgName
            every { userHandle } returns handle
            every { installId } returns InstallId(pkgId, handle)
            every { isSystemApp } returns false
            every { isUpdatedSystemApp } returns false
            every { installerInfo } returns InstallerInfo()
        }
    }

    private fun realArchiveSupport(pkgOps: PkgOps): ArchiveSupport {
        mockkObject(BuildWrap)
        mockkObject(BuildWrap.VERSION)
        every { BuildWrap.VERSION.SDK_INT } returns 35
        every { BuildWrap.VERSION.CODENAME } returns "REL"
        val context = mockk<Context>().apply {
            // PackageManager.isAppArchivable is API 35 and absent from the SDK 33 Robolectric runtime.
            every { packageManager } throws IllegalStateException("No isAppArchivable on SDK 33")
        }
        val userManager = mockk<UserManager2>().apply {
            coEvery { currentUser() } returns UserProfile2(handle = user)
        }
        return ArchiveSupport(context = context, userManager2 = userManager, pkgOps = pkgOps)
    }

    private class Harness(
        val appScan: AppScan,
        val pkgOps: PkgOps,
        val repoData: MutableStateFlow<PkgRepo.PkgData>,
    )

    private fun harness(
        pkgs: Collection<Installed>,
        archiveSupport: (PkgOps) -> ArchiveSupport = { mockk(relaxed = true) },
    ): Harness {
        val repoData = MutableStateFlow(PkgRepo.PkgData.from(pkgs))
        val pkgRepo = mockk<PkgRepo>().apply {
            every { data } returns repoData
            coEvery { refresh() } answers { repoData.value.pkgs }
        }
        val pkgOps = mockk<PkgOps>().apply {
            every { sharedResource } returns SharedResource.createKeepAlive("pkgOps", keepAliveScope)
            coEvery { querySizeStats(any(), any()) } returns null
            coEvery { getArchivablePackages(any(), any()) } returns emptySet()
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
            archiveSupport = archiveSupport(pkgOps),
        )
        return Harness(appScan = appScan, pkgOps = pkgOps, repoData = repoData)
    }

    @Test
    fun `a scan without sizing does not run the bulk prefetch`() = runTest2 {
        val harness = harness(listOf(pkg("eu.thlab.one"), pkg("eu.thlab.two")))

        harness.appScan.allApps(user = null, includeUsage = false, includeActive = false, includeSize = false)

        coVerify(exactly = 0) { harness.pkgOps.querySizeStats(any(), any()) }
    }

    @Test
    fun `a sizeless scan does not poison the cache for a later sizing scan`() = runTest2 {
        val target = pkg("eu.thlab.target")
        val targetId = target.installId
        val harness = harness(listOf(target))

        harness.appScan.allApps(user = null, includeUsage = false, includeActive = false, includeSize = false)
        harness.appScan.allApps(user = null, includeUsage = false, includeActive = false, includeSize = true)

        coVerify(exactly = 1) { harness.pkgOps.querySizeStats(targetId, any()) }
    }

    @Test
    fun `a cancel lands inside the assembly loop instead of after it`() = runTest2 {
        // Nothing in the loop is guaranteed to suspend, so without an explicit ensureActive() a
        // cancel is only honoured once every package has been processed.
        lateinit var job: Job
        val seen = mutableListOf<String>()
        val pkgs = (1..5).map { index ->
            val pkgName = "eu.thlab.app$index"
            // packageName is read once per assembly-loop iteration (progress label), so it doubles
            // as the iteration counter and as the cancel trigger.
            pkg(pkgName).apply {
                every { packageName } answers {
                    seen += pkgName
                    if (seen.size == 2) job.cancel()
                    pkgName
                }
            }
        }
        val harness = harness(pkgs)

        var thrown: Throwable? = null
        job = launch {
            try {
                harness.appScan.allApps(
                    user = null,
                    includeUsage = false,
                    includeActive = false,
                    includeSize = false,
                )
            } catch (e: Throwable) {
                thrown = e
            }
        }
        job.join()

        thrown.shouldBeInstanceOf<CancellationException>()
        seen shouldBe listOf("eu.thlab.app1", "eu.thlab.app2")
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

    @Test
    fun `archivable packages are looked up once per other user until refresh`() = runTest2 {
        val pkgs = listOf(
            normalPkg("eu.thlab.one", otherUser),
            normalPkg("eu.thlab.two", otherUser),
            normalPkg("eu.thlab.three", otherUser),
            normalPkg("eu.thlab.four", thirdUser),
            normalPkg("eu.thlab.five", thirdUser),
        )
        val harness = harness(pkgs) { realArchiveSupport(it) }
        coEvery { harness.pkgOps.getArchivablePackages(otherUser, any()) } returns setOf("eu.thlab.two")
        coEvery { harness.pkgOps.getArchivablePackages(thirdUser, any()) } returns setOf("eu.thlab.five")

        val apps = harness.appScan.allApps(user = null, includeUsage = false, includeActive = false, includeSize = false)
        harness.appScan.allApps(user = null, includeUsage = false, includeActive = false, includeSize = false)

        apps.filter { it.canBeArchived }.map { it.installId }.toSet() shouldBe setOf(
            InstallId(Pkg.Id("eu.thlab.two"), otherUser),
            InstallId(Pkg.Id("eu.thlab.five"), thirdUser),
        )
        coVerify(exactly = 1) { harness.pkgOps.getArchivablePackages(otherUser, any()) }
        coVerify(exactly = 1) { harness.pkgOps.getArchivablePackages(thirdUser, any()) }

        harness.appScan.refresh()
        harness.appScan.allApps(user = null, includeUsage = false, includeActive = false, includeSize = false)

        coVerify(exactly = 2) { harness.pkgOps.getArchivablePackages(otherUser, any()) }
        coVerify(exactly = 2) { harness.pkgOps.getArchivablePackages(thirdUser, any()) }
    }

    @Test
    fun `current user apps never look up archivable packages`() = runTest2 {
        val pkgs = listOf(
            normalPkg("eu.thlab.one", user),
            normalPkg("eu.thlab.two", user),
        )
        val harness = harness(pkgs) { realArchiveSupport(it) }

        harness.appScan.allApps(user = null, includeUsage = false, includeActive = false, includeSize = false)

        coVerify(exactly = 0) { harness.pkgOps.getArchivablePackages(any(), any()) }
    }
}
