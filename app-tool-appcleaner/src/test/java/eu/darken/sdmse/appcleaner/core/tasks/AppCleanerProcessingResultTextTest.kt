package eu.darken.sdmse.appcleaner.core.tasks

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AppCleanerProcessingResultTextTest : BaseTest() {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun success(
        count: Int,
        stoppedEarly: AppCleanerTask.StopReason?,
        skipped: Int = 0,
    ) = AppCleanerProcessingTask.Success(
        affectedSpace = 1024L,
        affectedPaths = emptySet(),
        affectedCount = count,
        stoppedEarly = stoppedEarly,
        skippedCount = skipped,
    ).primaryInfo.get(context)

    @Test
    fun `a completed run just states what was deleted`() {
        success(count = 46, stoppedEarly = null) shouldBe "46 expendable items deleted"
    }

    @Test
    fun `a run stopped by a locked screen says so`() {
        success(count = 1, stoppedEarly = AppCleanerTask.StopReason.SCREEN_UNAVAILABLE) shouldBe
            "1 expendable item deleted, stopped because the screen was off or locked"
        success(count = 46, stoppedEarly = AppCleanerTask.StopReason.SCREEN_UNAVAILABLE) shouldBe
            "46 expendable items deleted, stopped because the screen was off or locked"
    }

    @Test
    fun `a run stopped by an error says so`() {
        success(count = 1, stoppedEarly = AppCleanerTask.StopReason.ERROR) shouldBe
            "1 expendable item deleted, stopped by an error"
        success(count = 46, stoppedEarly = AppCleanerTask.StopReason.ERROR) shouldBe
            "46 expendable items deleted, stopped by an error"
    }

    @Test
    fun `a run that skipped the accessibility service says how many caches are left`() {
        success(count = 1, stoppedEarly = AppCleanerTask.StopReason.AUTOMATION_NO_CONSENT, skipped = 1) shouldBe
            "1 expendable item deleted, 1 cache still needs the accessibility service"
        success(count = 46, stoppedEarly = AppCleanerTask.StopReason.AUTOMATION_NO_CONSENT, skipped = 1) shouldBe
            "46 expendable items deleted, 1 cache still needs the accessibility service"
        success(count = 1, stoppedEarly = AppCleanerTask.StopReason.AUTOMATION_NO_CONSENT, skipped = 3) shouldBe
            "1 expendable item deleted, 3 caches still need the accessibility service"
        success(count = 46, stoppedEarly = AppCleanerTask.StopReason.AUTOMATION_NO_CONSENT, skipped = 3) shouldBe
            "46 expendable items deleted, 3 caches still need the accessibility service"
    }
}
