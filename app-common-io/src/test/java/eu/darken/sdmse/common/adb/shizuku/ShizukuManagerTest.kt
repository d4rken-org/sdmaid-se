package eu.darken.sdmse.common.adb.shizuku

import eu.darken.sdmse.common.adb.AdbConnectTimeoutException
import eu.darken.sdmse.common.adb.AdbSettings
import eu.darken.sdmse.common.adb.AdbUnavailableException
import eu.darken.sdmse.common.adb.service.AdbServiceClient
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.sharedresource.Resource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.flow.test

class ShizukuManagerTest : BaseTest() {

    private val settings: AdbSettings = mockk()
    private val shizukuWrapper: ShizukuWrapper = mockk()
    private val serviceClient: AdbServiceClient = mockk(relaxed = true)

    private val useShizukuValue: DataStoreValue<Boolean?> = mockk()
    private lateinit var useShizukuFlow: MutableStateFlow<Boolean?>
    private lateinit var wrapperLink: MutableStateFlow<AdbLink?>
    private lateinit var scope: CoroutineScope

    private var linkSubscriptions = 0

    @BeforeEach
    fun setup() {
        linkSubscriptions = 0
        useShizukuFlow = MutableStateFlow(true)
        wrapperLink = MutableStateFlow(null)
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

        every { settings.useShizuku } returns useShizukuValue
        every { useShizukuValue.flow } returns useShizukuFlow

        every { shizukuWrapper.permissionChanges } returns emptyFlow()

        // Track whether the wrapper's link flow is ever collected.
        every { shizukuWrapper.link } returns wrapperLink.onStart { linkSubscriptions++ }
    }

    @AfterEach
    fun teardown() {
        scope.cancel()
    }

    private fun manager() = ShizukuManager(
        appScope = scope,
        dispatcherProvider = TestDispatcherProvider(),
        settings = settings,
        shizukuWrapper = shizukuWrapper,
        serviceClient = serviceClient,
    )

    /** Managers of both families, all of them belonging to the active backend. */
    private fun setShizukuPackages(vararg pkgs: String) = setManagers(
        all = pkgs.toList(),
        active = pkgs.toList(),
    )

    private fun setManagers(
        all: List<String>,
        active: List<String>,
        backend: AdbBackend = AdbBackend.SHIZUKU,
    ) {
        coEvery { shizukuWrapper.getManagerPackages() } returns all
        coEvery { shizukuWrapper.getActiveManagerPackages(any()) } returns active
        coEvery { shizukuWrapper.getActiveManagerPackage(any()) } returns active.firstOrNull()
        coEvery { shizukuWrapper.activeBackend() } returns backend
        coEvery { shizukuWrapper.backendOf(any()) } returns backend
    }

    // --- adbLink -------------------------------------------------------------------------------

    @Test fun `link follows the wrapper when the user opted in`() {
        val link = mockk<AdbLink>()
        wrapperLink.value = link
        val mgr = manager()

        val collector = mgr.adbLink.test(tag = "link", scope = scope)
        collector.await { values, _ -> values.any { it != null } }

        collector.latestValues.last() shouldBe link
        linkSubscriptions shouldBe 1

        wrapperLink.value = null
        collector.await { _, latest -> latest == null }

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `link stays null and untouched when the user opted out`() {
        wrapperLink.value = mockk()
        useShizukuFlow.value = false
        val mgr = manager()

        val collector = mgr.adbLink.test(tag = "link", scope = scope)
        collector.await { values, _ -> values.isNotEmpty() }

        collector.latestValues.last() shouldBe null
        linkSubscriptions shouldBe 0

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `link stays null when the user has not decided yet`() {
        wrapperLink.value = mockk()
        useShizukuFlow.value = null
        val mgr = manager()

        val collector = mgr.adbLink.test(tag = "link", scope = scope)
        collector.await { values, _ -> values.isNotEmpty() }

        collector.latestValues.last() shouldBe null
        linkSubscriptions shouldBe 0

        runBlocking { collector.cancelAndJoin() }
    }

    // --- installation --------------------------------------------------------------------------

    @Test fun `isInstalled is not cached and re-evaluates each call`() {
        val mgr = manager()

        setShizukuPackages()
        runBlocking { mgr.isInstalled() } shouldBe false

        // Shizuku gets installed afterwards: the next call must reflect it (no stale cache).
        setShizukuPackages(ShizukuManager.PKG_ID.name)
        runBlocking { mgr.isInstalled() } shouldBe true
    }

    @Test fun `getManagerId resolves the detected package`() {
        val mgr = manager()

        setShizukuPackages()
        runBlocking { mgr.getManagerId() } shouldBe null

        setShizukuPackages(ShizukuManager.PKG_ID.name)
        runBlocking { mgr.getManagerId() } shouldBe ShizukuManager.PKG_ID
    }

    @Test fun `getManagerId resolves a fork under a different package name`() {
        val forkPkg = "com.example.shizuku.fork"
        setShizukuPackages(forkPkg)
        val mgr = manager()

        runBlocking { mgr.getManagerId() } shouldBe forkPkg.toPkgId()
    }

    @Test fun `isOurServiceAvailable is false when isGranted is null`() {
        // null = "cannot know", e.g. no live link. Probing the service would block on the host
        // connection instead of failing fast.
        coEvery { shizukuWrapper.isGranted() } returns null
        val mgr = manager()

        runBlocking { mgr.isOurServiceAvailable() } shouldBe false

        coVerify(exactly = 0) { serviceClient.get() }
    }

    @Test fun `isOurServiceAvailable is false when the service client fails`() {
        coEvery { shizukuWrapper.isGranted() } returns true
        coEvery { serviceClient.get() } throws AdbUnavailableException("test")
        val mgr = manager()

        runBlocking { mgr.isOurServiceAvailable() } shouldBe false
    }

    @Test fun `managerIds always includes both reference packages plus any detected fork`() {
        val mgr = manager()

        // Nothing installed: just the reference packages of both families.
        setShizukuPackages()
        runBlocking { mgr.managerIds() } shouldBe setOf(ShizukuManager.PKG_ID, ShizukuManager.PORTER_PKG_ID)

        // Fork installed under a different package: the references and the fork are all protected.
        val forkPkg = "com.example.shizuku.fork"
        setShizukuPackages(forkPkg)
        runBlocking { mgr.managerIds() } shouldBe setOf(
            ShizukuManager.PKG_ID,
            ShizukuManager.PORTER_PKG_ID,
            forkPkg.toPkgId(),
        )
    }

    @Test fun `managerIds includes every detected manager package`() {
        // Shizuku+ next to its Compat Hub: the binder comes from Shizuku+, so its package has to be
        // covered too, not just the first one the permission lookup resolves.
        setShizukuPackages("moe.shizuku.privileged.api", "af.shizuku.plus.api")
        val mgr = manager()

        runBlocking { mgr.managerIds() } shouldBe setOf(
            ShizukuManager.PKG_ID,
            ShizukuManager.PORTER_PKG_ID,
            "af.shizuku.plus.api".toPkgId(),
        )
    }

    @Test fun `managerIds spans both families even when only one is active`() {
        // Porter installed while Shizuku is the active backend: it still has to be recognized as a
        // manager app, otherwise consumers stop protecting it.
        setManagers(
            all = listOf("moe.shizuku.privileged.api", "eu.darken.porter"),
            active = listOf("moe.shizuku.privileged.api"),
        )
        val mgr = manager()

        runBlocking { mgr.managerIds() } shouldBe setOf(ShizukuManager.PKG_ID, ShizukuManager.PORTER_PKG_ID)
        runBlocking { mgr.activeManagerIds() } shouldBe setOf(ShizukuManager.PKG_ID)
    }

    // --- active backend ------------------------------------------------------------------------

    @Test fun `getManagerId and isInstalled follow the active backend`() {
        setManagers(
            all = listOf("eu.darken.porter"),
            active = emptyList(),
        )
        val mgr = manager()

        runBlocking { mgr.getManagerId() } shouldBe null
        runBlocking { mgr.isInstalled() } shouldBe false

        setManagers(
            all = listOf("eu.darken.porter"),
            active = listOf("eu.darken.porter"),
            backend = AdbBackend.PORTER,
        )

        runBlocking { mgr.getManagerId() } shouldBe ShizukuManager.PORTER_PKG_ID
        runBlocking { mgr.isInstalled() } shouldBe true
    }

    @Test fun `referenceManagerId is the active backend's product package`() {
        val mgr = manager()

        setManagers(all = emptyList(), active = emptyList(), backend = AdbBackend.PORTER)
        runBlocking { mgr.referenceManagerId() } shouldBe ShizukuManager.PORTER_PKG_ID

        setManagers(all = emptyList(), active = emptyList(), backend = AdbBackend.SHIZUKU)
        runBlocking { mgr.referenceManagerId() } shouldBe ShizukuManager.PKG_ID
    }

    // --- priorityBlockedManagerId --------------------------------------------------------------

    private fun setShizukuFamilyManager(pkg: String?) {
        coEvery { shizukuWrapper.getActiveManagerPackage(AdbBackend.SHIZUKU) } returns pkg
    }

    @Test fun `priorityBlockedManagerId reports Shizuku while Porter is active and nothing is linked`() {
        coEvery { shizukuWrapper.activeBackend() } returns AdbBackend.PORTER
        setShizukuFamilyManager("moe.shizuku.privileged.api")
        wrapperLink.value = null
        val mgr = manager()

        runBlocking { mgr.priorityBlockedManagerId() } shouldBe ShizukuManager.PKG_ID
    }

    @Test fun `priorityBlockedManagerId is null while a link is held`() {
        coEvery { shizukuWrapper.activeBackend() } returns AdbBackend.PORTER
        setShizukuFamilyManager("moe.shizuku.privileged.api")
        wrapperLink.value = mockk()
        val mgr = manager()

        runBlocking { mgr.priorityBlockedManagerId() } shouldBe null
    }

    @Test fun `priorityBlockedManagerId is null while Shizuku is the active backend`() {
        coEvery { shizukuWrapper.activeBackend() } returns AdbBackend.SHIZUKU
        setShizukuFamilyManager("moe.shizuku.privileged.api")
        wrapperLink.value = null
        val mgr = manager()

        runBlocking { mgr.priorityBlockedManagerId() } shouldBe null
    }

    @Test fun `priorityBlockedManagerId is null when no Shizuku-family manager is installed`() {
        coEvery { shizukuWrapper.activeBackend() } returns AdbBackend.PORTER
        setShizukuFamilyManager(null)
        wrapperLink.value = null
        val mgr = manager()

        runBlocking { mgr.priorityBlockedManagerId() } shouldBe null
    }

    @Test fun `priorityBlockedManagerId reports a Shizuku fork`() {
        coEvery { shizukuWrapper.activeBackend() } returns AdbBackend.PORTER
        setShizukuFamilyManager("com.example.shizuku.fork")
        wrapperLink.value = null
        val mgr = manager()

        runBlocking { mgr.priorityBlockedManagerId() } shouldBe "com.example.shizuku.fork".toPkgId()
    }

    @Test fun `priorityBlockedManagerId with a known backend skips the lookup`() {
        setShizukuFamilyManager("moe.shizuku.privileged.api")
        wrapperLink.value = null
        val mgr = manager()

        runBlocking { mgr.priorityBlockedManagerId(AdbBackend.PORTER) } shouldBe ShizukuManager.PKG_ID
        runBlocking { mgr.priorityBlockedManagerId(AdbBackend.SHIZUKU) } shouldBe null

        coVerify(exactly = 0) { shizukuWrapper.activeBackend() }
    }

    // --- isShizukud ----------------------------------------------------------------------------

    @Test fun `isShizukud is false for an incompatible server`() {
        val incompatible = AdbAvailability.Incompatible(
            backend = AdbBackend.SHIZUKU,
            packageName = "moe.shizuku.privileged.api",
            serverTooOld = true,
            clientTooOld = false,
        )
        setShizukuPackages("moe.shizuku.privileged.api")
        coEvery { shizukuWrapper.availability() } returns incompatible
        coEvery { shizukuWrapper.isGranted() } returns true
        val mgr = manager()

        runBlocking { mgr.isShizukud() } shouldBe false

        coVerify(exactly = 0) { shizukuWrapper.isGranted() }
        coVerify(exactly = 0) { serviceClient.get() }
    }

    @Test fun `isShizukud is not blocked by an unknown availability`() {
        setShizukuPackages("moe.shizuku.privileged.api")
        coEvery { shizukuWrapper.availability() } returns null
        coEvery { shizukuWrapper.isGranted() } returns false
        val mgr = manager()

        runBlocking { mgr.isShizukud() } shouldBe false

        coVerify(exactly = 1) { shizukuWrapper.isGranted() }
    }

    @Test fun `isShizukud looks the availability up once`() {
        setShizukuPackages("moe.shizuku.privileged.api")
        coEvery { shizukuWrapper.availability() } returns AdbAvailability.Installed(
            backend = AdbBackend.SHIZUKU,
            packageName = "moe.shizuku.privileged.api",
            connected = true,
        )
        coEvery { shizukuWrapper.isGranted() } returns false
        val mgr = manager()

        runBlocking { mgr.isShizukud() } shouldBe false

        coVerify(exactly = 1) { shizukuWrapper.availability() }
        coVerify(exactly = 0) { shizukuWrapper.activeBackend() }
    }

    @Test fun `isShizukud is false when no manager is installed`() {
        setShizukuPackages()
        coEvery { shizukuWrapper.availability() } returns AdbAvailability.NotInstalled
        val mgr = manager()

        runBlocking { mgr.isShizukud() } shouldBe false

        coVerify(exactly = 0) { shizukuWrapper.isGranted() }
    }

    // --- getServiceState -----------------------------------------------------------------------

    @Test fun `getServiceState reports Unknown, not PermissionDenied, when isGranted is null`() {
        // null means "cannot know" (no live link), which resolves itself once the server runs.
        // Reporting it as a denial would tell the user to fix a permission that is not the problem.
        coEvery { shizukuWrapper.isGranted() } returns null
        val mgr = manager()

        runBlocking { mgr.getServiceState() } shouldBe ShizukuServiceState.Unknown

        coVerify(exactly = 0) { serviceClient.get() }
    }

    @Test fun `getServiceState reports PermissionDenied when isGranted is false`() {
        coEvery { shizukuWrapper.isGranted() } returns false
        val mgr = manager()

        runBlocking { mgr.getServiceState() } shouldBe ShizukuServiceState.PermissionDenied

        coVerify(exactly = 0) { serviceClient.get() }
    }

    @Test fun `getServiceState reports TimedOut for a direct connect timeout`() {
        coEvery { shizukuWrapper.isGranted() } returns true
        coEvery { serviceClient.get() } throws AdbConnectTimeoutException("test")
        val mgr = manager()

        runBlocking { mgr.getServiceState() } shouldBe ShizukuServiceState.TimedOut
    }

    @Test fun `getServiceState reports TimedOut for a wrapped connect timeout`() {
        // How it actually arrives: AdbServiceClient wraps the launcher's failure on its way out.
        coEvery { shizukuWrapper.isGranted() } returns true
        coEvery { serviceClient.get() } throws AdbUnavailableException(
            "wrapped",
            cause = AdbConnectTimeoutException("did not connect"),
        )
        val mgr = manager()

        runBlocking { mgr.getServiceState() } shouldBe ShizukuServiceState.TimedOut
    }

    @Test fun `getServiceState reports Failed for a generic failure`() {
        // The same upstream defect can surface as a handshake failure rather than a timeout, so this
        // has to be a reportable terminal state too, not an "unknown yet".
        coEvery { shizukuWrapper.isGranted() } returns true
        coEvery { serviceClient.get() } throws AdbUnavailableException("test")
        val mgr = manager()

        runBlocking { mgr.getServiceState() } shouldBe ShizukuServiceState.Failed
    }

    @Test fun `getServiceState propagates cancellation instead of reporting a failure`() {
        coEvery { shizukuWrapper.isGranted() } returns true
        coEvery { serviceClient.get() } throws CancellationException("cancelled")
        val mgr = manager()

        shouldThrow<CancellationException> { runBlocking { mgr.getServiceState() } }
    }

    @Test fun `terminal failures are the ones that offer a retry`() {
        ShizukuServiceState.TimedOut.isTerminalFailure shouldBe true
        ShizukuServiceState.Failed.isTerminalFailure shouldBe true
        ShizukuServiceState.NotChecked.isTerminalFailure shouldBe false
        ShizukuServiceState.Available.isTerminalFailure shouldBe false
        ShizukuServiceState.PermissionDenied.isTerminalFailure shouldBe false
        ShizukuServiceState.Unknown.isTerminalFailure shouldBe false
    }

    @Test fun `getServiceState reports Failed when the host hands back nothing usable`() {
        // Connected, but checkBase() returned null: a connection we cannot use is a failure, not an
        // "unknown yet" that would leave the card waiting forever.
        coEvery { shizukuWrapper.isGranted() } returns true
        val connection: AdbServiceClient.Connection = mockk()
        every { connection.ipc.checkBase() } returns null
        coEvery { serviceClient.get() } returns Resource(connection, mockk(relaxed = true))
        val mgr = manager()

        runBlocking { mgr.getServiceState() } shouldBe ShizukuServiceState.Failed
    }
}
