package eu.darken.sdmse.common.theming

import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ThemeColorProviderTest : BaseTest() {

    @Test
    fun `every color x style combination resolves to a non-null light scheme`() {
        for (color in ThemeColor.entries) {
            for (style in ThemeStyle.entries) {
                ThemeColorProvider.getLightColorScheme(color, style) shouldNotBe null
            }
        }
    }

    @Test
    fun `every color x style combination resolves to a non-null dark scheme`() {
        for (color in ThemeColor.entries) {
            for (style in ThemeStyle.entries) {
                ThemeColorProvider.getDarkColorScheme(color, style) shouldNotBe null
            }
        }
    }
}
