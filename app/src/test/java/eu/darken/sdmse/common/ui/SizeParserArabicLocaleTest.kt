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
@Config(sdk = [33], application = TestApplication::class, qualifiers = "ar")
class SizeParserArabicLocaleTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun createParser(): SizeParser = SizeParser(context)

    @Test
    fun `parse with Arabic-Indic numerals`() {
        val parser = createParser()
        // Arabic-Indic numerals (٠١٢٣٤٥٦٧٨٩) with Arabic unit names
        // Arabic units: بايت (B), كيلوبايت (KB), ميغابايت (MB), غيغابايت (GB)
        parser.parse("١ ميغابايت") shouldBe 1_000_000L
        parser.parse("١٠ ميغابايت") shouldBe 10_000_000L
        parser.parse("١ميغابايت") shouldBe 1_000_000L
    }

    @Test
    fun `parse with standard numerals and Arabic units`() {
        val parser = createParser()
        // Standard numerals with Arabic unit names
        parser.parse("1 ميغابايت") shouldBe 1_000_000L
        parser.parse("1ميغابايت") shouldBe 1_000_000L
        parser.parse("1 كيلوبايت") shouldBe 1_000L
        parser.parse("1 غيغابايت") shouldBe 1_000_000_000L
    }
}
