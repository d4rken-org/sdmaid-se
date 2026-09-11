package eu.darken.sdmse.common.pkgs.pkgops

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class PkgOpsFunnelTest : BaseTest() {

    private val testPkgId = "test.pkg".toPkgId()
    private val testUser = UserHandle2(0)

    private val context = mockk<Context>()
    private val packageManager = mockk<PackageManager>()
    private val rootManager = mockk<RootManager>()
    private val adbManager = mockk<AdbManager>()
    private val usageStatsManager = mockk<UsageStatsManager>()
    private val storageStatsManager = mockk<StorageStatsManager>()
    private val userManager2 = mockk<UserManager2>()

    private val testAppInfo = ApplicationInfo().apply {
        packageName = "test.pkg"
        labelRes = 0
        nonLocalizedLabel = "TestLabel"
    }
    private val testPkgInfo = PackageInfo().apply {
        packageName = "test.pkg"
        applicationInfo = testAppInfo
    }

    @BeforeEach
    fun setup() {
        // API 26 makes IPCFunnel build with a single permit. hasApiLevel(n) means "device SDK >= n",
        // so a single monotonic answer keeps every call consistent.
        mockkStatic("eu.darken.sdmse.common.BuildWrapKt")
        every { hasApiLevel(any()) } answers { 26 >= firstArg<Int>() }

        every { context.packageManager } returns packageManager
        every { packageManager.getPackageInfo("test.pkg", 0) } returns testPkgInfo
        coEvery { userManager2.currentUser() } returns UserProfile2(handle = testUser)
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    private fun create(scope: CoroutineScope) = PkgOps(
        appScope = scope,
        dispatcherProvider = TestDispatcherProvider(),
        context = context,
        ipcFunnel = IPCFunnel(context, TestDispatcherProvider()),
        rootManager = rootManager,
        adbManager = adbManager,
        usageStatsManager = usageStatsManager,
        storageStatsManager = storageStatsManager,
        userManager2 = userManager2,
    )

    @Test
    fun `queryPkg completes with a single funnel permit`() = runTest2 {
        val pkgOps = create(this)

        val result = withTimeout(5_000) {
            pkgOps.queryPkg(testPkgId, 0L, testUser, PkgOps.Mode.NORMAL)
        }

        result shouldBe testPkgInfo
    }

    @Test
    fun `getLabel completes with a single funnel permit`() = runTest2 {
        val pkgOps = create(this)

        val result = withTimeout(5_000) {
            pkgOps.getLabel(testPkgId)
        }

        result shouldBe "TestLabel"
    }
}
