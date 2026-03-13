package eu.darken.sdmse.common.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SizeParserTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun createParser(): SizeParser = SizeParser(context)

    @Test
    fun `parse bytes with space`() {
        val parser = createParser()
        parser.parse("1 B") shouldBe 1L
        parser.parse("100 B") shouldBe 100L
    }

    @Test
    fun `parse bytes without space`() {
        val parser = createParser()
        parser.parse("1B") shouldBe 1L
        parser.parse("100B") shouldBe 100L
    }

    @Test
    fun `parse kilobytes with space`() {
        val parser = createParser()
        parser.parse("1 kB") shouldBe 1_000L
        parser.parse("1 KB") shouldBe 1_000L
        parser.parse("10 KB") shouldBe 10_000L
    }

    @Test
    fun `parse kilobytes without space`() {
        val parser = createParser()
        parser.parse("1kB") shouldBe 1_000L
        parser.parse("1KB") shouldBe 1_000L
        parser.parse("10KB") shouldBe 10_000L
    }

    @Test
    fun `parse megabytes with space`() {
        val parser = createParser()
        parser.parse("1 MB") shouldBe 1_000_000L
        parser.parse("100 MB") shouldBe 100_000_000L
    }

    @Test
    fun `parse megabytes without space`() {
        val parser = createParser()
        parser.parse("1MB") shouldBe 1_000_000L
        parser.parse("100MB") shouldBe 100_000_000L
    }

    @Test
    fun `parse gigabytes with space`() {
        val parser = createParser()
        parser.parse("1 GB") shouldBe 1_000_000_000L
        parser.parse("10 GB") shouldBe 10_000_000_000L
    }

    @Test
    fun `parse gigabytes without space`() {
        val parser = createParser()
        parser.parse("1GB") shouldBe 1_000_000_000L
        parser.parse("10GB") shouldBe 10_000_000_000L
    }

    @Test
    fun `parse decimal values with period`() {
        val parser = createParser()
        parser.parse("1.5 MB") shouldBe 1_500_000L
        parser.parse("2.5 GB") shouldBe 2_500_000_000L
        parser.parse("0.5 KB") shouldBe 500L
    }

    @Test
    fun `parse decimal values without space`() {
        val parser = createParser()
        parser.parse("1.5MB") shouldBe 1_500_000L
        parser.parse("2.5GB") shouldBe 2_500_000_000L
    }

    @Test
    fun `parse with extra whitespace`() {
        val parser = createParser()
        parser.parse("  1 MB  ") shouldBe 1_000_000L
        parser.parse("1  MB") shouldBe 1_000_000L
    }

    @Test
    fun `case insensitive units`() {
        val parser = createParser()
        parser.parse("1 mb") shouldBe 1_000_000L
        parser.parse("1 Mb") shouldBe 1_000_000L
        parser.parse("1 mB") shouldBe 1_000_000L
    }

    @Test
    fun `invalid input returns null`() {
        val parser = createParser()
        parser.parse("").shouldBeNull()
        parser.parse("abc").shouldBeNull()
        parser.parse("MB").shouldBeNull()
        parser.parse("1").shouldBeNull()
        parser.parse("1 XB").shouldBeNull()
        parser.parse("1 TB").shouldBeNull() // TB not supported
    }

    @Test
    fun `negative numbers are not parsed`() {
        val parser = createParser()
        parser.parse("-1 MB").shouldBeNull()
    }
}
