package eu.darken.sdmse.common.adb.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.IBinder
import eu.darken.porter.sdk.UserServiceArgs
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Covers [ShizukuWrapper.getActiveManagerPackage] — permission-based manager detection that survives
 * "Hide Shizuku from other apps" mode, forks that rename their package (issue #2405) and forks that
 * declare their own permission name instead of the stock one — the split between the active
 * backend's family and "any manager at all", and the bounded, never-throwing calls on the link.
 */
class ShizukuWrapperTest {

    private val context = mockk<Context>()
    private val packageManager = mockk<PackageManager>()

    private val porterPermission = "eu.darken.porter.permission.API"
    private val stockPermission = "moe.shizuku.manager.permission.API_V23"
    private val plusPermission = "af.shizuku.plus.permission.API_V23"

    private val dispatcherProvider = object : DispatcherProvider {
        override val IO: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private class FakeLink(
        override val backend: AdbBackend = AdbBackend.SHIZUKU,
        override val uid: Int = 2000,
        val granted: MutableStateFlow<Boolean> = MutableStateFlow(true),
        val check: suspend () -> AdbPermission = {
            if (granted.value) AdbPermission.GRANTED else AdbPermission.DENIED
        },
        val request: suspend () -> AdbPermission = { AdbPermission.GRANTED },
        override val permission: Flow<Boolean> = granted,
    ) : AdbLink {
        override suspend fun checkPermission(): AdbPermission = check()
        override suspend fun requestPermission(): AdbPermission = request()
        override fun userService(args: UserServiceArgs): Flow<IBinder> = emptyFlow()
        override suspend fun stopUserService(args: UserServiceArgs) {}
    }

    private class FakeGateway(
        link: AdbLink? = null,
        val onAvailability: suspend () -> AdbAvailability = { AdbAvailability.NotInstalled },
    ) : PorterGateway {
        val linkFlow = MutableStateFlow(link)
        override val link: Flow<AdbLink?> = linkFlow
        override suspend fun availability(): AdbAvailability = onAvailability()
    }

    private fun installed(backend: AdbBackend, connected: Boolean = false) =
        AdbAvailability.Installed(backend = backend, packageName = "some.manager", connected = connected)

    private fun wrapper(
        backend: AdbBackend = AdbBackend.SHIZUKU,
        gateway: PorterGateway = FakeGateway(onAvailability = { installed(backend) }),
    ): ShizukuWrapper {
        every { context.packageManager } returns packageManager
        return ShizukuWrapper(context, dispatcherProvider, gateway)
    }

    // mockk gives us a real (Objenesis-instantiated) PermissionInfo whose inherited public
    // packageName field we can set directly, without invoking the Android constructor.
    private fun permissionInfo(pkg: String?) = mockk<PermissionInfo>().apply { packageName = pkg }

    private fun definePermission(name: String, owner: String?) {
        every { packageManager.getPermissionInfo(name, any<Int>()) } returns permissionInfo(owner)
    }

    private fun undefinePermission(name: String) {
        every { packageManager.getPermissionInfo(name, any<Int>()) } throws PackageManager.NameNotFoundException()
    }

    /**
     * Every permission has to be stubbed EXPLICITLY. An unstubbed lookup throws MockKException,
     * which [ShizukuWrapper] swallows in its generic catch and reports as "not declared" - so a
     * genuinely broken lookup would pass the test while only logging a WARN.
     */
    private fun definePermissions(porter: String?, stock: String?, plus: String?) {
        porter?.let { definePermission(porterPermission, it) } ?: undefinePermission(porterPermission)
        stock?.let { definePermission(stockPermission, it) } ?: undefinePermission(stockPermission)
        plus?.let { definePermission(plusPermission, it) } ?: undefinePermission(plusPermission)
    }

    @Test
    fun `resolves the declaring package when the Shizuku permission exists`() = runTest {
        definePermissions(porter = null, stock = "moe.shizuku.privileged.api", plus = null)

        wrapper().getActiveManagerPackage() shouldBe "moe.shizuku.privileged.api"
    }

    @Test
    fun `resolves a fork declaring the permission under a different package`() = runTest {
        definePermissions(porter = null, stock = "com.example.shizuku.fork", plus = null)

        wrapper().getActiveManagerPackage() shouldBe "com.example.shizuku.fork"
    }

    @Test
    fun `returns null when no app declares the Shizuku permission`() = runTest {
        definePermissions(porter = null, stock = null, plus = null)

        wrapper().getActiveManagerPackage() shouldBe null
    }

    @Test
    fun `returns null on unexpected PackageManager failure`() = runTest {
        every { packageManager.getPermissionInfo(any(), any<Int>()) } throws RuntimeException("OEM quirk")

        wrapper().getActiveManagerPackage() shouldBe null
    }

    @Test
    fun `returns null when the declaring package name is blank`() = runTest {
        definePermissions(porter = "", stock = "", plus = "")

        wrapper().getActiveManagerPackage() shouldBe null
    }

    @Test
    fun `falls back to the Shizuku+ permission when the stock permission is undefined`() = runTest {
        definePermissions(porter = null, stock = null, plus = "af.shizuku.plus.api")

        wrapper().getActiveManagerPackage() shouldBe "af.shizuku.plus.api"
    }

    @Test
    fun `prefers the stock permission owner when both are defined`() = runTest {
        definePermissions(porter = null, stock = "moe.shizuku.privileged.api", plus = "af.shizuku.plus.api")
        val wrapper = wrapper()

        wrapper.getActiveManagerPackage() shouldBe "moe.shizuku.privileged.api"
        wrapper.getActiveManagerPackages() shouldBe listOf("moe.shizuku.privileged.api", "af.shizuku.plus.api")
    }

    @Test
    fun `collapses one app defining both permissions to a single entry`() = runTest {
        definePermissions(porter = null, stock = "moe.shizuku.privileged.api", plus = "moe.shizuku.privileged.api")

        wrapper().getActiveManagerPackages() shouldBe listOf("moe.shizuku.privileged.api")
    }

    @Test
    fun `getManagerPackages is empty when no permission is defined`() = runTest {
        definePermissions(porter = null, stock = null, plus = null)
        val wrapper = wrapper()

        wrapper.getManagerPackages() shouldBe emptyList()
        wrapper.getActiveManagerPackage() shouldBe null
    }

    @Test
    fun `a failing lookup for one permission does not hide the other`() = runTest {
        undefinePermission(porterPermission)
        every { packageManager.getPermissionInfo(stockPermission, any<Int>()) } throws RuntimeException("OEM quirk")
        definePermission(plusPermission, "af.shizuku.plus.api")

        wrapper().getActiveManagerPackage() shouldBe "af.shizuku.plus.api"
    }

    // --- backend split -------------------------------------------------------------------------

    @Test
    fun `under Porter the active manager is the Porter permission owner`() = runTest {
        definePermissions(porter = "eu.darken.porter", stock = "moe.shizuku.privileged.api", plus = null)

        wrapper(backend = AdbBackend.PORTER).getActiveManagerPackage() shouldBe "eu.darken.porter"
    }

    @Test
    fun `under Porter the Shizuku permissions are never consulted`() = runTest {
        // Offering the other family's manager would send the user to an app that cannot affect the
        // link this process is waiting on.
        definePermissions(porter = "eu.darken.porter", stock = "moe.shizuku.privileged.api", plus = "af.shizuku.plus.api")

        wrapper(backend = AdbBackend.PORTER).getActiveManagerPackages() shouldBe listOf("eu.darken.porter")

        verify(exactly = 0) { packageManager.getPermissionInfo(stockPermission, any<Int>()) }
        verify(exactly = 0) { packageManager.getPermissionInfo(plusPermission, any<Int>()) }
    }

    @Test
    fun `under Shizuku the Porter permission is never consulted`() = runTest {
        definePermissions(porter = "eu.darken.porter", stock = "moe.shizuku.privileged.api", plus = null)

        wrapper(backend = AdbBackend.SHIZUKU).getActiveManagerPackages() shouldBe
            listOf("moe.shizuku.privileged.api")

        verify(exactly = 0) { packageManager.getPermissionInfo(porterPermission, any<Int>()) }
    }

    @Test
    fun `an explicit backend skips the availability lookup`() = runTest {
        definePermissions(porter = "eu.darken.porter", stock = "moe.shizuku.privileged.api", plus = null)
        var lookups = 0
        val gateway = FakeGateway(onAvailability = {
            lookups++
            installed(AdbBackend.PORTER)
        })

        wrapper(gateway = gateway).getActiveManagerPackage(AdbBackend.SHIZUKU) shouldBe "moe.shizuku.privileged.api"

        lookups shouldBe 0
    }

    @Test
    fun `getManagerPackages spans both families regardless of the active backend`() = runTest {
        // Feeds "is this app an ADB manager", e.g. AppCleaner's protection of the manager app.
        definePermissions(porter = "eu.darken.porter", stock = "moe.shizuku.privileged.api", plus = null)

        val expected = listOf("eu.darken.porter", "moe.shizuku.privileged.api")
        wrapper(backend = AdbBackend.PORTER).getManagerPackages() shouldBe expected
        wrapper(backend = AdbBackend.SHIZUKU).getManagerPackages() shouldBe expected
    }

    // --- availability / backend ----------------------------------------------------------------

    @Test
    fun `backendOf follows the availability snapshot`() = runTest {
        val wrapper = wrapper()

        wrapper.backendOf(installed(AdbBackend.PORTER)) shouldBe AdbBackend.PORTER
        wrapper.backendOf(installed(AdbBackend.SHIZUKU, connected = true)) shouldBe AdbBackend.SHIZUKU
        wrapper.backendOf(
            AdbAvailability.Incompatible(AdbBackend.PORTER, "eu.darken.porter", serverTooOld = true, clientTooOld = false)
        ) shouldBe AdbBackend.PORTER
        wrapper.backendOf(AdbAvailability.NotInstalled) shouldBe AdbBackend.SHIZUKU
    }

    @Test
    fun `unknown availability falls back to whether a package declares Porter's permission`() = runTest {
        val wrapper = wrapper()

        definePermissions(porter = "eu.darken.porter", stock = "moe.shizuku.privileged.api", plus = null)
        wrapper.backendOf(null) shouldBe AdbBackend.PORTER

        definePermissions(porter = null, stock = "moe.shizuku.privileged.api", plus = null)
        wrapper.backendOf(null) shouldBe AdbBackend.SHIZUKU
    }

    @Test
    fun `availability passes the gateway's snapshot through`() = runTest {
        val incompatible = AdbAvailability.Incompatible(AdbBackend.SHIZUKU, null, serverTooOld = false, clientTooOld = true)

        wrapper(gateway = FakeGateway(onAvailability = { incompatible })).availability() shouldBe incompatible
    }

    @Test
    fun `availability is null when the gateway does not answer in time`() = runTest {
        val wrapper = wrapper(gateway = FakeGateway(onAvailability = { awaitCancellation() }))

        wrapper.availability() shouldBe null
    }

    @Test
    fun `availability is null when the gateway throws`() = runTest {
        val wrapper = wrapper(gateway = FakeGateway(onAvailability = { throw IllegalStateException("remote boom") }))

        wrapper.availability() shouldBe null
    }

    @Test
    fun `availability propagates cancellation`() = runTest {
        val wrapper = wrapper(gateway = FakeGateway(onAvailability = { throw CancellationException("cancelled") }))

        shouldThrow<CancellationException> { wrapper.availability() }
    }

    @Test
    fun `activeBackend falls back to the permission owner when availability times out or fails`() = runTest {
        definePermissions(porter = "eu.darken.porter", stock = "moe.shizuku.privileged.api", plus = null)

        wrapper(gateway = FakeGateway(onAvailability = { awaitCancellation() })).activeBackend() shouldBe AdbBackend.PORTER
        wrapper(gateway = FakeGateway(onAvailability = { throw IllegalStateException() })).activeBackend() shouldBe
            AdbBackend.PORTER

        definePermissions(porter = null, stock = "moe.shizuku.privileged.api", plus = null)
        wrapper(gateway = FakeGateway(onAvailability = { awaitCancellation() })).activeBackend() shouldBe AdbBackend.SHIZUKU
    }

    @Test
    fun `activeBackend is Shizuku when nothing is installed`() = runTest {
        wrapper(gateway = FakeGateway(onAvailability = { AdbAvailability.NotInstalled })).activeBackend() shouldBe
            AdbBackend.SHIZUKU
    }

    // --- permission / isGranted ---------------------------------------------------------------

    @Test
    fun `permission is null without a link`() = runTest {
        wrapper(gateway = FakeGateway(link = null)).permission() shouldBe null
    }

    @Test
    fun `permission passes the link's answer through`() = runTest {
        AdbPermission.entries.forEach { answer ->
            wrapper(gateway = FakeGateway(link = FakeLink(check = { answer }))).permission() shouldBe answer
        }
    }

    @Test
    fun `isGranted is false for a permanent denial`() = runTest {
        val wrapper = wrapper(gateway = FakeGateway(link = FakeLink(check = { AdbPermission.DENIED_PERMANENTLY })))

        wrapper.isGranted() shouldBe false
    }

    @Test
    fun `isGranted is null without a link`() = runTest {
        wrapper(gateway = FakeGateway(link = null)).isGranted() shouldBe null
    }

    @Test
    fun `isGranted reflects checkPermission`() = runTest {
        val granted = MutableStateFlow(true)
        val wrapper = wrapper(gateway = FakeGateway(link = FakeLink(granted = granted)))

        wrapper.isGranted() shouldBe true

        granted.value = false
        wrapper.isGranted() shouldBe false
    }

    @Test
    fun `isGranted gives up when the link does not answer`() = runTest {
        val wrapper = wrapper(gateway = FakeGateway(link = FakeLink(check = { awaitCancellation() })))

        wrapper.isGranted() shouldBe null
    }

    @Test
    fun `isGranted is null when the call fails`() = runTest {
        val wrapper = wrapper(gateway = FakeGateway(link = FakeLink(check = { throw IllegalStateException("remote") })))

        wrapper.isGranted() shouldBe null
    }

    @Test
    fun `isGranted propagates cancellation`() = runTest {
        val wrapper = wrapper(gateway = FakeGateway(link = FakeLink(check = { throw CancellationException("cancelled") })))

        shouldThrow<CancellationException> { wrapper.isGranted() }
    }

    // --- requestPermission ---------------------------------------------------------------------

    @Test
    fun `requestPermission is null without a link`() = runTest {
        wrapper(gateway = FakeGateway(link = null)).requestPermission() shouldBe null
    }

    @Test
    fun `requestPermission returns the user's answer`() = runTest {
        AdbPermission.entries.forEach { answer ->
            wrapper(gateway = FakeGateway(link = FakeLink(request = { answer }))).requestPermission() shouldBe answer
        }
    }

    @Test
    fun `requestPermission is null when the link is lost meanwhile`() = runTest {
        // Stands in for the SDK's PorterConnectionLostException, which can't be constructed here.
        val wrapper = wrapper(gateway = FakeGateway(link = FakeLink(request = { throw IllegalStateException("lost") })))

        wrapper.requestPermission() shouldBe null
    }

    @Test
    fun `requestPermission propagates cancellation`() = runTest {
        val wrapper = wrapper(gateway = FakeGateway(link = FakeLink(request = { throw CancellationException("cancelled") })))

        shouldThrow<CancellationException> { wrapper.requestPermission() }
    }

    @Test
    fun `requestPermission waits for a user who takes longer than the IPC budget`() = runTest {
        val wrapper = wrapper(
            gateway = FakeGateway(
                link = FakeLink(request = {
                    delay(ShizukuWrapper.IPC_TIMEOUT_MS * 4)
                    AdbPermission.GRANTED
                }),
            ),
        )

        wrapper.requestPermission() shouldBe AdbPermission.GRANTED
    }

    // --- link state ----------------------------------------------------------------------------

    @Test
    fun `serverUid is the link's uid, null without a link`() = runTest {
        val gateway = FakeGateway(link = null)
        val wrapper = wrapper(gateway = gateway)

        wrapper.serverUid() shouldBe null

        gateway.linkFlow.value = FakeLink(uid = 2000)
        wrapper.serverUid() shouldBe 2000
    }

    @Test
    fun `permissionChanges emits on changes of the current link's permission only`() = runTest {
        val firstGranted = MutableStateFlow(false)
        val gateway = FakeGateway(link = FakeLink(granted = firstGranted))
        val wrapper = wrapper(gateway = gateway)

        val changes = mutableListOf<Unit>()
        val job = launch { wrapper.permissionChanges.collect { changes += it } }
        runCurrent()
        changes.size shouldBe 0 // the state a link attached with is not a change

        firstGranted.value = true
        runCurrent()
        changes.size shouldBe 1

        val secondGranted = MutableStateFlow(true)
        gateway.linkFlow.value = FakeLink(granted = secondGranted)
        runCurrent()
        changes.size shouldBe 1

        firstGranted.value = false // the replaced link no longer counts
        runCurrent()
        changes.size shouldBe 1

        secondGranted.value = false
        runCurrent()
        changes.size shouldBe 2

        job.cancel()
    }

    @Test
    fun `permissionChanges survives a failing permission flow and follows the next link`() = runTest {
        val broken = flow<Boolean> {
            emit(true)
            throw IllegalStateException("boom")
        }
        val gateway = FakeGateway(link = FakeLink(permission = broken))
        val wrapper = wrapper(gateway = gateway)

        val changes = mutableListOf<Unit>()
        val failure = CompletableDeferred<Throwable>()
        val job = launch {
            try {
                wrapper.permissionChanges.collect { changes += it }
            } catch (e: Throwable) {
                failure.complete(e)
            }
        }
        runCurrent()
        failure.isCompleted shouldBe false

        val granted = MutableStateFlow(false)
        gateway.linkFlow.value = FakeLink(granted = granted)
        runCurrent()
        granted.value = true
        runCurrent()

        changes.size shouldBe 1
        failure.isCompleted shouldBe false
        job.cancel()
    }
}
