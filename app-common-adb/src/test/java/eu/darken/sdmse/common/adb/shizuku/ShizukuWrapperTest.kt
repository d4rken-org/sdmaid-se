package eu.darken.sdmse.common.adb.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

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

    private fun wrapper(): ShizukuWrapper {
        every { context.packageManager } returns packageManager
        return ShizukuWrapper(context, dispatcherProvider)
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
}
