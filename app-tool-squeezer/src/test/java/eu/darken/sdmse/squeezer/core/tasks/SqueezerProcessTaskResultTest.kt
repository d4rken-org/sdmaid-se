package eu.darken.sdmse.squeezer.core.tasks

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SqueezerProcessTaskResultTest : BaseTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `guard-skipped photos appear in the result summary`() {
        // Items skipped at process time to preserve HDR/depth data used to vanish silently:
        // pruned from the pending list but counted neither as processed nor failed.
        val success = SqueezerProcessTask.Success(
            affectedSpace = 1024L,
            affectedPaths = emptySet(),
            processedCount = 2,
            guardSkippedCount = 3,
        )

        val summary = success.secondaryInfo.get(context)
        summary shouldContain "3 skipped"
        summary shouldContain "protected"
    }

    @Test
    fun `summary omits the skip clause when nothing was guard-skipped`() {
        val success = SqueezerProcessTask.Success(
            affectedSpace = 1024L,
            affectedPaths = emptySet(),
            processedCount = 2,
        )

        success.secondaryInfo.get(context) shouldNotContain "protected"
    }

    @Test
    fun `not-smaller files appear in the result summary`() {
        val success = SqueezerProcessTask.Success(
            affectedSpace = 1024L,
            affectedPaths = emptySet(),
            processedCount = 2,
            unchangedCount = 2,
        )

        success.secondaryInfo.get(context) shouldContain "2 unchanged (not smaller)"
    }

    @Test
    fun `summary omits the unchanged clause when every file shrank`() {
        val success = SqueezerProcessTask.Success(
            affectedSpace = 1024L,
            affectedPaths = emptySet(),
            processedCount = 2,
            unchangedCount = 0,
        )

        success.secondaryInfo.get(context) shouldNotContain "not smaller"
    }
}
