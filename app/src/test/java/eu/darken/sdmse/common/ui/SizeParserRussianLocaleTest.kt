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
@Config(sdk = [33], application = TestApplication::class, qualifiers = "ru")
class SizeParserRussianLocaleTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun createParser(): SizeParser = SizeParser(context)

    @Test
    fun `parse Cyrillic units in Russian locale`() {
        val parser = createParser()
        // Russian locale uses Cyrillic unit abbreviations
        parser.parse("1 МБ") shouldBe 1_000_000L
        parser.parse("1МБ") shouldBe 1_000_000L
        parser.parse("1 ГБ") shouldBe 1_000_000_000L
        parser.parse("1ГБ") shouldBe 1_000_000_000L
    }

    @Test
    fun `parse decimal with comma in Russian locale`() {
        val parser = createParser()
        // Russian uses comma as decimal separator
        parser.parse("1,5 МБ") shouldBe 1_500_000L
        parser.parse("1,5МБ") shouldBe 1_500_000L
    }
}
