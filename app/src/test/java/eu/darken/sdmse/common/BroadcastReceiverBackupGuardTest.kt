package eu.darken.sdmse.common

import android.app.Application
import android.content.Context
import android.content.Intent
import eu.darken.sdmse.appcontrol.core.restore.UnarchiveManager
import eu.darken.sdmse.appcontrol.core.restore.UnarchiveReceiver
import eu.darken.sdmse.corpsefinder.core.watcher.ExternalWatcherTask
import eu.darken.sdmse.corpsefinder.core.watcher.ExternalWatcherTaskReceiver
import eu.darken.sdmse.corpsefinder.core.watcher.UninstallWatcherReceiver
import eu.darken.sdmse.scheduler.core.SchedulerRestoreReceiver
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Regression test for https://github.com/d4rken-org/sdmaid-se/issues/1274
 *
 * During Android Auto Backup, BroadcastReceivers are invoked with a RestrictedContext
 * whose applicationContext is a plain android.app.Application (not our @HiltAndroidApp App).
 * Receivers must silently return without crashing.
 */
class BroadcastReceiverBackupGuardTest : BaseTest() {

    private val restrictedContext = mockk<Context>(relaxed = true).apply {
        every { applicationContext } returns mockk<Application>()
    }

    @Test
    fun `SchedulerRestoreReceiver does not crash with restricted context`() {
        val intent = mockk<Intent>(relaxed = true).apply {
            every { action } returns Intent.ACTION_BOOT_COMPLETED
        }
        SchedulerRestoreReceiver().onReceive(restrictedContext, intent)
    }

    @Test
    fun `UnarchiveReceiver does not crash with restricted context`() {
        val intent = mockk<Intent>(relaxed = true).apply {
            every { action } returns UnarchiveManager.ACTION_UNARCHIVE_RESULT
            every { getIntExtra(UnarchiveManager.EXTRA_REQUEST_CODE, -1) } returns 42
        }
        UnarchiveReceiver().onReceive(restrictedContext, intent)
    }

    @Test
    fun `UninstallWatcherReceiver does not crash with restricted context`() {
        val intent = mockk<Intent>(relaxed = true).apply {
            every { action } returns Intent.ACTION_PACKAGE_FULLY_REMOVED
            every { data } returns mockk {
                every { schemeSpecificPart } returns "com.example.app"
            }
        }
        UninstallWatcherReceiver().onReceive(restrictedContext, intent)
    }

    @Test
    fun `ExternalWatcherTaskReceiver does not crash with restricted context`() {
        val intent = mockk<Intent>(relaxed = true).apply {
            every { action } returns ExternalWatcherTaskReceiver.TASK_INTENT
            @Suppress("DEPRECATION")
            every { getParcelableExtra<ExternalWatcherTask>(ExternalWatcherTaskReceiver.EXTRA_TASK) } returns mockk<ExternalWatcherTask.Delete>()
        }
        ExternalWatcherTaskReceiver().onReceive(restrictedContext, intent)
    }
}
