package eu.darken.sdmse.corpsefinder.core.watcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.runTest2

/**
 * Drive the scheduler with `runCurrent()`, never `advanceUntilIdle()`. The coordinator collects on the
 * scope it is handed, which is [TestScope.backgroundScope] here, and `advanceUntilIdle()` returns as soon
 * as no *foreground* work is left - with the test body idle that is immediately, so the collector never
 * even starts and every "no write happened" assertion passes for the wrong reason.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class UninstallWatcherControlTest : BaseTest() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val component = ComponentName(context, UninstallWatcherReceiver::class.java)
    private val logRecorder = LogRecorder()

    @Before fun installLogRecorder() = Logging.install(logRecorder)

    @After fun removeLogRecorder() = Logging.remove(logRecorder)

    private class LogRecorder : Logging.Logger {
        val entries = mutableListOf<Pair<Logging.Priority, String>>()
        override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
            entries.add(priority to "$tag: $message")
        }
    }

    /** A fresh mock per call: two Infos with the same content are non-equal, so MutableStateFlow won't conflate them. */
    private fun info(
        isPro: Boolean,
        isSettled: Boolean = true,
        error: Throwable? = null,
    ): UpgradeRepo.Info = mockk<UpgradeRepo.Info>().apply {
        every { this@apply.isPro } returns isPro
        every { this@apply.isSettled } returns isSettled
        every { this@apply.error } returns error
    }

    private class Harness(
        val control: UninstallWatcherControl,
        val packageManager: PackageManager,
        val watcherFlow: MutableStateFlow<Boolean>,
        val upgradeFlow: MutableStateFlow<UpgradeRepo.Info>,
    )

    private fun TestScope.harness(
        isWatcherEnabled: Boolean = true,
        entitlement: UpgradeRepo.Info = info(isPro = true),
        componentState: Int = PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        writeFails: Boolean = false,
        watcherSource: Flow<Boolean>? = null,
    ): Harness {
        val watcherFlow = MutableStateFlow(isWatcherEnabled)
        val upgradeFlow = MutableStateFlow(entitlement)
        val watcherValue = mockk<DataStoreValue<Boolean>>().apply {
            every { flow } returns (watcherSource ?: watcherFlow)
        }
        val settings = mockk<CorpseFinderSettings>().apply {
            every { this@apply.isWatcherEnabled } returns watcherValue
        }
        val upgradeRepo = mockk<UpgradeRepo>().apply {
            every { upgradeInfo } returns upgradeFlow
        }
        val packageManager = mockk<PackageManager>(relaxed = true).apply {
            every { getComponentEnabledSetting(component) } returns componentState
            if (writeFails) {
                every { setComponentEnabledSetting(any(), any(), any()) } throws RuntimeException("ROM said no")
            }
        }
        val control = UninstallWatcherControl(
            context = context,
            // The test scope as @AppScope: an exception escaping the collector fails the test.
            appScope = backgroundScope,
            packageManager = packageManager,
            settings = settings,
            upgradeRepo = upgradeRepo,
        )
        return Harness(control, packageManager, watcherFlow, upgradeFlow)
    }

    private fun Harness.verifyWrites(enabled: Int = 0, disabled: Int = 0) {
        verify(exactly = enabled) {
            packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        verify(exactly = disabled) {
            packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    @Test
    fun `a disabled component is enabled for a pro user who wants the watcher`() = runTest2 {
        val h = harness(
            isWatcherEnabled = true,
            entitlement = info(isPro = true),
            componentState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        )

        h.control.start()
        runCurrent()

        h.verifyWrites(enabled = 1)
    }

    @Test
    fun `an already enabled component is left alone`() = runTest2 {
        val h = harness(
            isWatcherEnabled = true,
            entitlement = info(isPro = true),
            componentState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        )

        h.control.start()
        runCurrent()

        verify(exactly = 0) { h.packageManager.setComponentEnabledSetting(any(), any(), any()) }
    }

    @Test
    fun `a component at its manifest default is enabled for a pro user who wants the watcher`() = runTest2 {
        val h = harness(
            isWatcherEnabled = true,
            entitlement = info(isPro = true),
            componentState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
        )

        h.control.start()
        runCurrent()

        h.verifyWrites(enabled = 1)
    }

    @Test
    fun `an enabled component is disabled when the watcher preference is off`() = runTest2 {
        val h = harness(
            isWatcherEnabled = false,
            entitlement = info(isPro = true),
            componentState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        )

        h.control.start()
        runCurrent()

        h.verifyWrites(disabled = 1)
    }

    @Test
    fun `an unsettled entitlement leaves the component untouched`() = runTest2 {
        val h = harness(
            isWatcherEnabled = true,
            entitlement = info(isPro = false, isSettled = false),
            componentState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        )

        h.control.start()
        runCurrent()

        verify(exactly = 0) { h.packageManager.setComponentEnabledSetting(any(), any(), any()) }
    }

    @Test
    fun `a settled entitlement carrying an error leaves the component untouched`() = runTest2 {
        val h = harness(
            isWatcherEnabled = true,
            entitlement = info(isPro = false, isSettled = true, error = IllegalStateException("cache read failed")),
            componentState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        )

        h.control.start()
        runCurrent()

        verify(exactly = 0) { h.packageManager.setComponentEnabledSetting(any(), any(), any()) }
    }

    @Test
    fun `an enabled component is disabled for a confirmed non-pro user`() = runTest2 {
        val h = harness(
            isWatcherEnabled = true,
            entitlement = info(isPro = false),
            componentState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        )

        h.control.start()
        runCurrent()

        h.verifyWrites(disabled = 1)
    }

    @Test
    fun `an entitlement that settles as pro after start enables the component`() = runTest2 {
        val h = harness(
            isWatcherEnabled = true,
            entitlement = info(isPro = false, isSettled = false),
            componentState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        )

        h.control.start()
        runCurrent()
        verify(exactly = 0) { h.packageManager.setComponentEnabledSetting(any(), any(), any()) }

        h.upgradeFlow.value = info(isPro = true)
        runCurrent()

        h.verifyWrites(enabled = 1)
    }

    @Test
    fun `an entitlement that is lost after start disables the component`() = runTest2 {
        val h = harness(
            isWatcherEnabled = true,
            entitlement = info(isPro = true),
            componentState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        )

        h.control.start()
        runCurrent()
        verify(exactly = 0) { h.packageManager.setComponentEnabledSetting(any(), any(), any()) }

        h.upgradeFlow.value = info(isPro = false)
        runCurrent()

        h.verifyWrites(disabled = 1)
    }

    @Test
    fun `a watcher preference change after start reconciles`() = runTest2 {
        val h = harness(
            isWatcherEnabled = false,
            entitlement = info(isPro = true),
            componentState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        )

        h.control.start()
        runCurrent()
        verify(exactly = 0) { h.packageManager.setComponentEnabledSetting(any(), any(), any()) }

        h.watcherFlow.value = true
        runCurrent()

        h.verifyWrites(enabled = 1)
    }

    @Test
    fun `a failed write is retried by the next emission carrying the same desired state`() = runTest2 {
        val h = harness(
            isWatcherEnabled = true,
            entitlement = info(isPro = true),
            componentState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            writeFails = true,
        )

        h.control.start()
        runCurrent()

        // Same desired state, different Info instance: a dedup on the desired value would swallow this.
        h.upgradeFlow.value = info(isPro = true)
        runCurrent()

        h.verifyWrites(enabled = 2)
    }

    @Test
    fun `a throwing settings flow is contained and logged`() = runTest2 {
        val h = harness(
            watcherSource = flow { throw IllegalStateException("corrupt preferences") },
        )

        h.control.start()
        runCurrent()

        verify(exactly = 0) { h.packageManager.setComponentEnabledSetting(any(), any(), any()) }
        logRecorder.entries.any {
            it.first == Logging.Priority.WARN && it.second.startsWith("SDMSE:CorpseFinder:Watcher:Uninstall:Control:")
        } shouldBe true
    }
}
