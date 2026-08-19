package eu.darken.sdmse.stats.core

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.main.ui.MainActivity
import eu.darken.sdmse.stats.core.forecast.StorageForecast
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class LowSpaceNotificationsTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun notifications(granted: Boolean = true): LowSpaceNotifications {
        val app = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
        if (granted) {
            app.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            app.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
        return LowSpaceNotifications(context = context, notificationManager = notificationManager)
    }

    private fun lastPosted(): Notification = Shadows
        .shadowOf(notificationManager)
        .getNotification(LowSpaceNotifications.NOTIFICATION_ID)

    private fun Notification.title(): String = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun Notification.body(): String = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    @Test
    fun `the content intent does not collapse into the task manager PendingIntents`() {
        // filterEquals ignores extras, and TaskWorkerNotifications / TaskResultNotifications both
        // hold request code 0 against this bare intent. Sharing the shape would let
        // FLAG_UPDATE_CURRENT rewrite THEIR extras and send their taps to the Analyzer.
        val taskManagerIntent = Intent(context, MainActivity::class.java)

        lowSpaceContentIntent(context).filterEquals(taskManagerIntent) shouldBe false
        LowSpaceNotifications.REQUEST_CODE shouldBe 3000
    }

    @Test
    fun `the content intent routes to the Analyzer`() {
        val intent = lowSpaceContentIntent(context)

        intent.component?.className shouldBe MainActivity::class.java.name
        intent.action shouldBe LowSpaceNotifications.ACTION_LOW_SPACE
    }

    @Test
    fun `the predictive variant warns about filling up and renders the day count`() {
        notifications().notifyLowSpace(
            forecast = StorageForecast.Filling(daysUntilFloor = 3, bytesPerDay = 1_000_000_000L, isUrgent = true),
            freeBytes = 3_000_000_000L,
        ) shouldBe LowSpaceNotifications.PostResult.POSTED

        val posted = lastPosted()
        posted.title() shouldBe "Storage is filling up"
        posted.body() shouldContain "3 days"
    }

    @Test
    fun `the below-floor variant warns that storage is almost full and says it is running out`() {
        // A trend title ("filling up") over a "running out" body understates the emergency.
        notifications().notifyLowSpace(
            forecast = StorageForecast.BelowFloor,
            freeBytes = 1_000_000_000L,
        ) shouldBe LowSpaceNotifications.PostResult.POSTED

        val posted = lastPosted()
        posted.title() shouldBe "Storage is almost full"
        posted.body() shouldContain "running out"
    }

    @Test
    fun `a missing POST_NOTIFICATIONS permission blocks instead of failing`() {
        // API 33+ without the runtime permission must report BLOCKED so the caller keeps its latch
        // armed for a later grant.
        notifications(granted = false).notifyLowSpace(
            forecast = StorageForecast.BelowFloor,
            freeBytes = 1_000_000_000L,
        ) shouldBe LowSpaceNotifications.PostResult.BLOCKED
    }
}
