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
import kotlinx.coroutines.flow.flow
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
    private lateinit var scope: CoroutineScope

    private var binderSubscriptions = 0

    @BeforeEach
    fun setup() {
        binderSubscriptions = 0
        useShizukuFlow = MutableStateFlow(true)
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

        every { settings.useShizuku } returns useShizukuValue
        every { useShizukuValue.flow } returns useShizukuFlow

        every { shizukuWrapper.permissionGrantEvents } returns emptyFlow()

        // Track whether the underlying Shizuku binder flow is ever collected.
        every { shizukuWrapper.baseServiceBinder } returns flow {
            binderSubscriptions++
            emit(mockk<ShizukuBaseServiceBinder>())
        }
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

    private fun setShizukuPackages(vararg pkgs: String) {
        coEvery { shizukuWrapper.getManagerPackages() } returns pkgs.toList()
        coEvery { shizukuWrapper.getManagerPackage() } returns pkgs.firstOrNull()
    }

    @Test fun `binder is not probed when Shizuku is not installed`() {
        setShizukuPackages()
        val mgr = manager()

        val collector = mgr.shizukuBinder.test(tag = "binder", scope = scope)
        collector.await { values, _ -> values.isNotEmpty() }

        collector.latestValues.last() shouldBe null
        binderSubscriptions shouldBe 0

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `binder is probed when Shizuku is installed`() {
        setShizukuPackages(ShizukuManager.PKG_ID.name)
        val mgr = manager()

        val collector = mgr.shizukuBinder.test(tag = "binder", scope = scope)
        collector.await { values, _ -> values.any { it != null } }

        binderSubscriptions shouldBe 1

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `binder stays closed when user opted out even if installed`() {
        setShizukuPackages(ShizukuManager.PKG_ID.name)
        useShizukuFlow.value = false
        val mgr = manager()

        val collector = mgr.shizukuBinder.test(tag = "binder", scope = scope)
        collector.await { values, _ -> values.isNotEmpty() }

        collector.latestValues.last() shouldBe null
        binderSubscriptions shouldBe 0

        runBlocking { collector.cancelAndJoin() }
    }

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
        // null = "cannot know", e.g. no live Shizuku binder. Probing the service would block on the
        // host connection instead of failing fast.
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

    @Test fun `managerIds always includes the reference package plus any detected fork`() {
        val mgr = manager()

        // Nothing installed: just the reference package.
        setShizukuPackages()
        runBlocking { mgr.managerIds() } shouldBe setOf(ShizukuManager.PKG_ID)

        // Fork installed under a different package: both the reference and the fork are protected.
        val forkPkg = "com.example.shizuku.fork"
        setShizukuPackages(forkPkg)
        runBlocking { mgr.managerIds() } shouldBe setOf(ShizukuManager.PKG_ID, forkPkg.toPkgId())
    }

    @Test fun `managerIds includes every detected manager package`() {
        // Shizuku+ next to its Compat Hub: the binder comes from Shizuku+, so its package has to be
        // covered too, not just the first one the permission lookup resolves.
        setShizukuPackages("moe.shizuku.privileged.api", "af.shizuku.plus.api")
        val mgr = manager()

        runBlocking { mgr.managerIds() } shouldBe setOf(ShizukuManager.PKG_ID, "af.shizuku.plus.api".toPkgId())
    }

    // --- getServiceState -----------------------------------------------------------------------

    @Test fun `getServiceState reports Unknown, not PermissionDenied, when isGranted is null`() {
        // null means "cannot know" (no live binder), which resolves itself once Shizuku runs.
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
