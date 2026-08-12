package eu.darken.sdmse.common.theming

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import io.kotest.assertions.withClue
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

    @Test
    fun `surface container roles are not left at M3 baseline defaults`() {
        val baselineLight = lightColorScheme()
        val baselineDark = darkColorScheme()
        for (color in ThemeColor.entries) {
            for (style in ThemeStyle.entries) {
                checkSurfaceRoles(
                    label = "$color/$style/light",
                    scheme = ThemeColorProvider.getLightColorScheme(color, style),
                    baseline = baselineLight,
                    // Light schemes legitimately use pure white for surfaceContainerLowest, same as the baseline.
                    checkLowest = false,
                )
                checkSurfaceRoles(
                    label = "$color/$style/dark",
                    scheme = ThemeColorProvider.getDarkColorScheme(color, style),
                    baseline = baselineDark,
                    checkLowest = true,
                )
            }
        }
    }

    private fun checkSurfaceRoles(label: String, scheme: ColorScheme, baseline: ColorScheme, checkLowest: Boolean) {
        // Schemes built via lightColorScheme()/darkColorScheme() silently fill unspecified roles
        // with the purple-seeded M3 baseline palette.
        if (checkLowest) {
            withClue("$label surfaceContainerLowest") { scheme.surfaceContainerLowest shouldNotBe baseline.surfaceContainerLowest }
        }
        withClue("$label surfaceDim") { scheme.surfaceDim shouldNotBe baseline.surfaceDim }
        withClue("$label surfaceBright") { scheme.surfaceBright shouldNotBe baseline.surfaceBright }
        withClue("$label surfaceContainerLow") { scheme.surfaceContainerLow shouldNotBe baseline.surfaceContainerLow }
        withClue("$label surfaceContainer") { scheme.surfaceContainer shouldNotBe baseline.surfaceContainer }
        withClue("$label surfaceContainerHigh") { scheme.surfaceContainerHigh shouldNotBe baseline.surfaceContainerHigh }
        withClue("$label surfaceContainerHighest") { scheme.surfaceContainerHighest shouldNotBe baseline.surfaceContainerHighest }
    }
}
