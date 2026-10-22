package eu.darken.sdmse.corpsefinder.core.watcher

import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class UninstallWatcherNotificationsTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var packageManagerCalls = 0

        override fun getPackageManager(): PackageManager {
            packageManagerCalls++
            return super.getPackageManager()
        }
    }

    private fun notifications() = UninstallWatcherNotifications(context, notificationManager)

    private fun assertChannelCreatedAndPosted() {
        notificationManager.notificationChannels.shouldHaveSize(1)
        notificationManager.notificationChannels.single().id shouldEndWith CHANNEL_ID_SUFFIX
        Shadows.shadowOf(notificationManager).getNotification(NOTIFICATION_ID).shouldNotBeNull()
    }

    @Test
    fun `construction touches neither the NotificationManager nor the PackageManager`() {
        val recordingContext = RecordingContext(context)
        val mockManager = mockk<NotificationManager>(relaxed = true)

        UninstallWatcherNotifications(recordingContext, mockManager)

        verify { mockManager wasNot Called }
        recordingContext.packageManagerCalls shouldBe 0
    }

    @Test
    fun `construction creates no channel`() {
        notifications()

        notificationManager.notificationChannels.shouldBeEmpty()
    }

    @Test
    fun `a scan result as the first post creates the channel and posts`() {
        notifications().notifyOfScan(
            ExternalWatcherResult.Scan(
                pkgId = "com.example.app".toPkgId(),
                foundItems = 2,
            ),
        )

        assertChannelCreatedAndPosted()
    }

    @Test
    fun `a deletion result as the first post creates the channel and posts`() {
        notifications().notifyOfDeletion(
            ExternalWatcherResult.Deletion(
                appName = "Example".toCaString(),
                pkgId = "com.example.app".toPkgId(),
                deletedItems = 2,
                freedSpace = 1_000_000L,
            ),
        )

        assertChannelCreatedAndPosted()
    }

    companion object {
        private const val CHANNEL_ID_SUFFIX = ".notification.channel.corpsefinder.watcher.uninstall"
        private const val NOTIFICATION_ID = 75
    }
}
