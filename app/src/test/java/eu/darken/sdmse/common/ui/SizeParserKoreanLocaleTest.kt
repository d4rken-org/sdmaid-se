package eu.darken.sdmse.common.ui

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
@Config(sdk = [33], application = TestApplication::class, qualifiers = "ko")
class SizeParserKoreanLocaleTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun createParser(): SizeParser = SizeParser(context)

    @Test
    fun `parse standard units in Korean locale`() {
        val parser = createParser()
        // Should still work with standard unit abbreviations even in Korean locale
        parser.parse("1 KB") shouldBe 1_000L
        parser.parse("1KB") shouldBe 1_000L
        parser.parse("1 MB") shouldBe 1_000_000L
        parser.parse("1MB") shouldBe 1_000_000L
        parser.parse("1 GB") shouldBe 1_000_000_000L
        parser.parse("1GB") shouldBe 1_000_000_000L
    }

    @Test
    fun `parse decimal in Korean locale`() {
        val parser = createParser()
        parser.parse("1.5 MB") shouldBe 1_500_000L
        parser.parse("1.5MB") shouldBe 1_500_000L
    }
}
