package eu.darken.sdmse.corpsefinder.core.tasks

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.files.local.LocalPath
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class UninstallWatcherTaskResultTextTest : BaseTest() {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun success(
        foundItems: Int,
        deleted: Int = 0,
    ) = UninstallWatcherTask.Success(
        foundItems = foundItems,
        affectedSpace = 0L,
        affectedPaths = (1..deleted).map { LocalPath.build("storage", "corpse$it") }.toSet(),
    ).primaryInfo.get(context)

    @Test
    fun `a scan-only run states what was discovered`() {
        success(foundItems = 3) shouldBe "3 app remnants discovered"
        success(foundItems = 1) shouldBe "1 app remnant discovered"
        success(foundItems = 0) shouldBe "0 app remnants discovered"
    }

    @Test
    fun `a deleting run states what was deleted`() {
        success(foundItems = 2, deleted = 2) shouldBe "2 app remnants deleted"
        success(foundItems = 1, deleted = 1) shouldBe "1 app remnant deleted"
    }

    @Test
    fun `a partial deletion counts only the deleted paths`() {
        success(foundItems = 3, deleted = 1) shouldBe "1 app remnant deleted"
    }
}
