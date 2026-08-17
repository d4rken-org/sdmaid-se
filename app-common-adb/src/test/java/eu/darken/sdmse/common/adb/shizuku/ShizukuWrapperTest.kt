package eu.darken.sdmse.common.adb.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import testhelpers.coroutine.TestDispatcherProvider
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.seconds

/**
 * Covers [ShizukuWrapper.getManagerPackage] — permission-based Shizuku detection that survives
 * "Hide Shizuku from other apps" mode and forks that rename their package (issue #2405) — and
 * [ShizukuWrapper.isGranted]'s binder-liveness gate.
 */
class ShizukuWrapperTest {

    private val context = mockk<Context>()
    private val packageManager = mockk<PackageManager>()

    private val dispatcherProvider = object : DispatcherProvider {
        override val IO: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private fun wrapper(
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        dispatchers: DispatcherProvider = dispatcherProvider,
    ): ShizukuWrapper {
        every { context.packageManager } returns packageManager
        return ShizukuWrapper(context, scope, dispatchers)
    }

    // mockk gives us a real (Objenesis-instantiated) PermissionInfo whose inherited public
    // packageName field we can set directly, without invoking the Android constructor.
    private fun permissionInfo(pkg: String?) = mockk<PermissionInfo>().apply { packageName = pkg }

    @Test
    fun `resolves the declaring package when the Shizuku permission exists`() = runTest {
        every { packageManager.getPermissionInfo(any(), any<Int>()) } returns
            permissionInfo("moe.shizuku.privileged.api")

        wrapper().getManagerPackage() shouldBe "moe.shizuku.privileged.api"
    }

    @Test
    fun `resolves a fork declaring the permission under a different package`() = runTest {
        every { packageManager.getPermissionInfo(any(), any<Int>()) } returns
            permissionInfo("com.example.shizuku.fork")

        wrapper().getManagerPackage() shouldBe "com.example.shizuku.fork"
    }

    @Test
    fun `returns null when no app declares the Shizuku permission`() = runTest {
        every { packageManager.getPermissionInfo(any(), any<Int>()) } throws
            PackageManager.NameNotFoundException()

        wrapper().getManagerPackage() shouldBe null
    }

    @Test
    fun `returns null on unexpected PackageManager failure`() = runTest {
        every { packageManager.getPermissionInfo(any(), any<Int>()) } throws RuntimeException("OEM quirk")

        wrapper().getManagerPackage() shouldBe null
    }

    @Test
    fun `returns null when the declaring package name is blank`() = runTest {
        every { packageManager.getPermissionInfo(any(), any<Int>()) } returns permissionInfo("")

        wrapper().getManagerPackage() shouldBe null
    }

    @Test
    fun `isGranted returns null when the binder is not alive`() = runTest {
        val wrapper = wrapper().apply {
            pingBinderAction = { false }
            // checkSelfPermission() latches process-wide and would still claim a grant here.
            checkSelfPermissionAction = { PackageManager.PERMISSION_GRANTED }
        }

        wrapper.isGranted() shouldBe null
    }

    @Test
    fun `isGranted returns null when pingBinder throws the null-race NPE`() = runTest {
        val wrapper = wrapper().apply {
            pingBinderAction = { throw NullPointerException("binder went away") }
            checkSelfPermissionAction = { PackageManager.PERMISSION_GRANTED }
        }

        wrapper.isGranted() shouldBe null
    }

    @Test
    fun `isGranted reflects checkSelfPermission when the binder is alive`() = runTest {
        val wrapper = wrapper().apply { pingBinderAction = { true } }

        wrapper.checkSelfPermissionAction = { PackageManager.PERMISSION_GRANTED }
        wrapper.isGranted() shouldBe true

        wrapper.checkSelfPermissionAction = { PackageManager.PERMISSION_DENIED }
        wrapper.isGranted() shouldBe false

        // Shizuku itself throws when it has no binder to ask, which also means "cannot know".
        wrapper.checkSelfPermissionAction = { throw IllegalStateException("binder haven't been received") }
        wrapper.isGranted() shouldBe null
    }

    @Test
    fun `isGranted gives up when the Shizuku binder wedges`() = runTest(timeout = 10.seconds) {
        // Shizuku.checkSelfPermission() does a real binder round-trip until its granted state has been
        // latched (first connection). Against a server that is alive but not servicing requests that
        // transaction never returns, and it runs before AdbHostLauncher's connect watchdog is armed,
        // so without a bound here the setup card spins forever.
        val entered = CompletableDeferred<Unit>()
        val wedge = CountDownLatch(1)
        // Real scope + real IO dispatcher: the wedge blocks an actual thread, Unconfined would run the
        // block inline and block the caller before the timeout could ever be applied.
        val realScope = CoroutineScope(SupervisorJob())
        val wrapper = wrapper(realScope, TestDispatcherProvider(Dispatchers.IO)).apply {
            ipcTimeoutMs = 250L
            pingBinderAction = { true }
            checkSelfPermissionAction = {
                entered.complete(Unit)
                wedge.await() // blocks the calling thread, unaffected by coroutine cancellation
                PackageManager.PERMISSION_GRANTED
            }
        }

        try {
            // The call runs in its OWN scope, not as a child of the test coroutine: on a regression
            // it blocks a thread non-cooperatively, and a blocked child would make the enclosing
            // withContext wait for it forever, deadlocking the JVM instead of failing the test.
            val result = realScope.async(Dispatchers.Default) { wrapper.isGranted() }

            withContext(Dispatchers.Default) {
                // Only assert once the wedge is real: the call has been entered and is blocked.
                withTimeout(5_000L) { entered.await() }
                withTimeout(5_000L) { result.await() } shouldBe null
            }
        } finally {
            wedge.countDown()
            realScope.cancel()
        }
    }

    @Test
    fun `isGranted still answers normally when the binder responds`() = runTest(timeout = 10.seconds) {
        // Guards the happy path against the detach+timeout wrapper: a prompt answer must survive it.
        val realScope = CoroutineScope(SupervisorJob())
        val wrapper = wrapper(realScope, TestDispatcherProvider(Dispatchers.IO)).apply {
            pingBinderAction = { true }
            checkSelfPermissionAction = { PackageManager.PERMISSION_GRANTED }
        }
        try {
            withContext(Dispatchers.Default) {
                withTimeout(5_000L) { wrapper.isGranted() }.shouldNotBeNull() shouldBe true
            }
        } finally {
            realScope.cancel()
        }
    }
}
