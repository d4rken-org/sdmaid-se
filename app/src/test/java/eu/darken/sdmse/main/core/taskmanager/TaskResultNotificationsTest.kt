package eu.darken.sdmse.main.core.taskmanager

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.main.core.SDMTool
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.io.IOException
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class TaskResultNotificationsTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun result(info: String): SDMTool.Task.Result = mockk(relaxed = true) {
        every { primaryInfo } returns info.toCaString()
    }

    private fun managedTask(
        result: SDMTool.Task.Result?,
        error: Throwable?,
    ) = TaskSubmitter.ManagedTask(
        id = "task-1",
        toolType = SDMTool.Type.APPCLEANER,
        task = mockk(relaxed = true),
        completedAt = Instant.now(),
        result = result,
        error = error,
    )

    private fun Notification.body(): String = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    @Test
    fun `a task that failed after freeing something reports what it freed`() {
        val notifications = TaskResultNotifications(context, notificationManager)

        val notification = notifications.getNotification(
            managedTask(result("46 expendable items deleted"), IOException("Screen went off")),
        )

        notification.body() shouldBe "46 expendable items deleted"
    }

    @Test
    fun `a task that failed with nothing to show falls back to the generic message`() {
        val notifications = TaskResultNotifications(context, notificationManager)

        val notification = notifications.getNotification(managedTask(null, IOException("Screen went off")))

        notification.body() shouldBe "Task didn't finish successfully."
    }
}
