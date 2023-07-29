package eu.darken.sdmse.common

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.text.DecimalFormatSymbols
import java.util.Locale


class ByteFormatterTest : BaseTest() {

    @Test fun `unit stripping`() {
        val ds = DecimalFormatSymbols(Locale.getDefault()).decimalSeparator
        ByteFormatter.stripSizeUnit("14 GB") shouldBe 14.0
        ByteFormatter.stripSizeUnit("14GB") shouldBe 14.0
        ByteFormatter.stripSizeUnit("14${ds}3GB") shouldBe 14.3
        ByteFormatter.stripSizeUnit("1${ds}6GB") shouldBe 1.6
    }
}