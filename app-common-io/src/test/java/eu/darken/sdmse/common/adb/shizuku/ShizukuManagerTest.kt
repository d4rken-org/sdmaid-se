package eu.darken.sdmse.common.adb.shizuku

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import eu.darken.sdmse.common.adb.AdbSettings
import eu.darken.sdmse.common.adb.service.AdbServiceClient
import eu.darken.sdmse.common.datastore.DataStoreValue
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.flow.test

class ShizukuManagerTest : BaseTest() {

    private val context: Context = mockk()
    private val packageManager: PackageManager = mockk()
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

        every { context.packageManager } returns packageManager
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
        context = context,
        appScope = scope,
        dispatcherProvider = TestDispatcherProvider(),
        settings = settings,
        shizukuWrapper = shizukuWrapper,
        serviceClient = serviceClient,
    )

    private fun setShizukuInstalled(installed: Boolean) {
        if (installed) {
            every { packageManager.getPackageInfo(ShizukuManager.PKG_ID.name, 0) } returns PackageInfo()
        } else {
            every {
                packageManager.getPackageInfo(ShizukuManager.PKG_ID.name, 0)
            } throws PackageManager.NameNotFoundException()
        }
    }

    @Test fun `binder is not probed when Shizuku is not installed`() {
        setShizukuInstalled(false)
        val mgr = manager()

        val collector = mgr.shizukuBinder.test(tag = "binder", scope = scope)
        collector.await { values, _ -> values.isNotEmpty() }

        collector.latestValues.last() shouldBe null
        binderSubscriptions shouldBe 0

        kotlinx.coroutines.runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `binder is probed when Shizuku is installed`() {
        setShizukuInstalled(true)
        val mgr = manager()

        val collector = mgr.shizukuBinder.test(tag = "binder", scope = scope)
        collector.await { values, _ -> values.any { it != null } }

        binderSubscriptions shouldBe 1

        kotlinx.coroutines.runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `binder stays closed when user opted out even if installed`() {
        setShizukuInstalled(true)
        useShizukuFlow.value = false
        val mgr = manager()

        val collector = mgr.shizukuBinder.test(tag = "binder", scope = scope)
        collector.await { values, _ -> values.isNotEmpty() }

        collector.latestValues.last() shouldBe null
        binderSubscriptions shouldBe 0

        kotlinx.coroutines.runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `isInstalled is not cached and re-evaluates each call`() {
        val mgr = manager()

        setShizukuInstalled(false)
        kotlinx.coroutines.runBlocking { mgr.isInstalled() } shouldBe false

        // Shizuku gets installed afterwards: the next call must reflect it (no stale cache).
        setShizukuInstalled(true)
        kotlinx.coroutines.runBlocking { mgr.isInstalled() } shouldBe true
    }
}
