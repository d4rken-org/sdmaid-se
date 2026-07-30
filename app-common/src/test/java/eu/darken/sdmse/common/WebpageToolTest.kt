package eu.darken.sdmse.common

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * The return value is a contract, not a convenience: the FOSS sponsor unlock heuristic only arms
 * when the page actually opened. Every swallow point has to report the failure back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class WebpageToolTest : BaseTest() {

    private val application: Application
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `a launched page reports success`() {
        WebpageTool(application).open(URL) shouldBe true

        shadowOf(application).nextStartedActivity!!.data shouldBe URL.toUri()
    }

    @Test
    fun `the android-tv stub handler is not a launch`() {
        // Android TV has no browser; the system stub would consume the intent and show an unhelpful
        // toast, so nothing is started at all.
        val intent = Intent(Intent.ACTION_VIEW, URL.toUri())
        shadowOf(application.packageManager).addResolveInfoForIntent(
            intent,
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "com.android.tv.frameworkpackagestubs"
                    name = "StubActivity"
                }
                isDefault = true
            },
        )

        WebpageTool(application).open(URL) shouldBe false

        shadowOf(application).nextStartedActivity shouldBe null
    }

    @Test
    fun `a missing browser reports failure`() {
        val context = object : ContextWrapper(application) {
            override fun startActivity(intent: Intent) = throw ActivityNotFoundException("No browser")
        }

        WebpageTool(context).open(URL) shouldBe false
    }

    @Test
    fun `a denied launch reports failure`() {
        val context = object : ContextWrapper(application) {
            override fun startActivity(intent: Intent) = throw SecurityException("Permission Denial")
        }

        WebpageTool(context).open(URL) shouldBe false
    }

    companion object {
        private const val URL = "https://github.com/sponsors/d4rken"
    }
}
