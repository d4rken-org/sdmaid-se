package eu.darken.sdmse.common.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.core.content.ContextCompat
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class GetInstalledAppsPermissionTest : BaseTest() {

    private val mockContext = mockk<Context>()
    private val mockPm = mockk<PackageManager>()

    @BeforeEach
    fun setup() {
        every { mockContext.packageManager } returns mockPm
        mockkStatic(ContextCompat::class)
    }

    @Test
    fun `permission not defined on device - returns true`() {
        every { mockPm.getPermissionInfo(any(), any<Int>()) } throws PackageManager.NameNotFoundException()

        Permission.GET_INSTALLED_APPS.isGranted(mockContext) shouldBe true
    }

    @Test
    fun `unexpected exception from PackageManager - returns true`() {
        every { mockPm.getPermissionInfo(any(), any<Int>()) } throws RuntimeException("OEM quirk")

        Permission.GET_INSTALLED_APPS.isGranted(mockContext) shouldBe true
    }

    @Test
    fun `permission exists and is granted - returns true`() {
        every { mockPm.getPermissionInfo(any(), any<Int>()) } returns mockk<PermissionInfo>()
        every {
            ContextCompat.checkSelfPermission(mockContext, "com.android.permission.GET_INSTALLED_APPS")
        } returns PackageManager.PERMISSION_GRANTED

        Permission.GET_INSTALLED_APPS.isGranted(mockContext) shouldBe true
    }

    @Test
    fun `permission exists and is denied - returns false`() {
        every { mockPm.getPermissionInfo(any(), any<Int>()) } returns mockk<PermissionInfo>()
        every {
            ContextCompat.checkSelfPermission(mockContext, "com.android.permission.GET_INSTALLED_APPS")
        } returns PackageManager.PERMISSION_DENIED

        Permission.GET_INSTALLED_APPS.isGranted(mockContext) shouldBe false
    }
}
