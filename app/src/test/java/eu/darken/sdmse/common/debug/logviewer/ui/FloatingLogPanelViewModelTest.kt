package eu.darken.sdmse.common.debug.logviewer.ui

import android.content.Intent
import android.net.Uri
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class FloatingLogPanelViewModelTest : BaseTest() {

    @Test
    fun `share intent uses the file name as subject`() {
        val uri = Uri.parse("content://eu.darken.sdmse.provider/debug/logview.txt")

        val intent = FloatingLogPanelViewModel.createShareIntent(uri, "logview.txt")

        intent.getStringExtra(Intent.EXTRA_SUBJECT) shouldBe "logview.txt"
        intent.action shouldBe Intent.ACTION_SEND
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) shouldBe uri
        intent.clipData.shouldNotBeNull().apply {
            itemCount shouldBe 1
            getItemAt(0).uri shouldBe uri
        }
        intent.type shouldBe "text/plain"
        (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) shouldBe Intent.FLAG_GRANT_READ_URI_PERMISSION
        (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) shouldBe Intent.FLAG_ACTIVITY_NEW_TASK
    }
}
