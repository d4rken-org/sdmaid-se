package eu.darken.sdmse.common.compose.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import kotlin.math.abs

/**
 * Guards the settings row layout: the value must render as its own line below the title instead of
 * competing with the title/subtitle column for horizontal space. A long localized value (e.g. the
 * Polish "Automatyczne (domyślnie)") used to take the width it needed and squeeze the text column
 * down to a few characters per line.
 *
 * [SettingsBaseItem] applies `combinedClickable` to its outer column, which merges descendant
 * semantics — a plain text lookup resolves to the whole row, not the individual `Text`. Every
 * layout assertion here therefore runs against the unmerged tree.
 */
class SettingsPreferenceItemTest : BaseComposeRobolectricTest() {

    @Test
    fun `value renders below the title, not beside it`() {
        composeRule.setContent {
            PreviewWrapper {
                Container {
                    SettingsPreferenceItem(
                        modifier = Modifier.testTag(TAG_VALUE),
                        title = TITLE,
                        subtitle = SUBTITLE,
                        value = VALUE,
                        onClick = {},
                    )
                }
            }
        }

        val titleBounds = title(TAG_VALUE).getUnclippedBoundsInRoot()
        val valueBounds = value(TAG_VALUE).getUnclippedBoundsInRoot()

        withClue("value top ${valueBounds.top} must sit below title bottom ${titleBounds.bottom}") {
            (valueBounds.top >= titleBounds.bottom) shouldBe true
        }
    }

    @Test
    fun `long value does not squeeze the title`() {
        composeRule.setContent {
            PreviewWrapper {
                Container {
                    SettingsPreferenceItem(
                        modifier = Modifier.testTag(TAG_PLAIN),
                        title = TITLE,
                        subtitle = SUBTITLE,
                        onClick = {},
                    )
                    SettingsPreferenceItem(
                        modifier = Modifier.testTag(TAG_VALUE),
                        title = TITLE,
                        subtitle = SUBTITLE,
                        value = VALUE,
                        onClick = {},
                    )
                }
            }
        }

        val plainTitle = title(TAG_PLAIN)
        val valuedTitle = title(TAG_VALUE)

        val plainWidth = plainTitle.getUnclippedBoundsInRoot().widthDp
        val valuedWidth = valuedTitle.getUnclippedBoundsInRoot().widthDp
        withClue("title width with value ($valuedWidth) must match the value-less row ($plainWidth)") {
            valuedWidth shouldBe plainWidth
        }

        val plainLines = plainTitle.textLayoutResult().lineCount
        val valuedLines = valuedTitle.textLayoutResult().lineCount
        withClue("title line count with value ($valuedLines) must match the value-less row ($plainLines)") {
            valuedLines shouldBe plainLines
        }
    }

    /**
     * "Not ellipsized" is asserted as "no characters were dropped": the last line's visible end
     * covers the whole string and the layout isn't height/line truncated. `hasVisualOverflow` is
     * unusable here — under Robolectric's font stack `didOverflowWidth` is true for every `Text`,
     * including the untouched title, even when a 24px line sits in a 328px constraint.
     */
    @Test
    fun `long value is not ellipsized`() {
        composeRule.setContent {
            PreviewWrapper {
                Container {
                    SettingsPreferenceItem(
                        modifier = Modifier.testTag(TAG_VALUE),
                        title = TITLE,
                        subtitle = SUBTITLE,
                        value = VALUE,
                        onClick = {},
                    )
                }
            }
        }

        val layout = value(TAG_VALUE).textLayoutResult()

        withClue("value must not be line-limited") {
            layout.layoutInput.maxLines shouldBe Int.MAX_VALUE
        }
        layout.didOverflowHeight shouldBe false
        withClue("last visible line must end at the full string length") {
            layout.getLineEnd(layout.lineCount - 1, visibleEnd = true) shouldBe VALUE.length
        }
    }

    @Test
    fun `merged semantics order is title, value, subtitle`() {
        composeRule.setContent {
            PreviewWrapper {
                Container {
                    SettingsPreferenceItem(
                        modifier = Modifier.testTag(TAG_VALUE),
                        title = TITLE,
                        subtitle = SUBTITLE,
                        value = VALUE,
                        onClick = {},
                    )
                }
            }
        }

        mergedTexts(TAG_VALUE) shouldBe listOf(TITLE, VALUE, SUBTITLE)
    }

    @Test
    fun `upgrade badge coexists with the value`() {
        var clicks = 0
        var upgrades = 0
        composeRule.setContent {
            PreviewWrapper {
                CompositionLocalProvider(LocalUpgradeBadgeLabel provides BADGE_LABEL) {
                    Container {
                        SettingsPreferenceItem(
                            modifier = Modifier.testTag(TAG_VALUE),
                            title = TITLE,
                            subtitle = SUBTITLE,
                            value = VALUE,
                            requiresUpgrade = true,
                            onClick = { clicks++ },
                            onUpgrade = { upgrades++ },
                        )
                    }
                }
            }
        }

        val titleBounds = title(TAG_VALUE).getUnclippedBoundsInRoot()
        val badgeBounds = composeRule
            .onNodeWithText(BADGE_LABEL, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val valueBounds = value(TAG_VALUE).getUnclippedBoundsInRoot()

        withClue("badge ($badgeBounds) must stay on the title line ($titleBounds)") {
            (badgeBounds.top < titleBounds.bottom && titleBounds.top < badgeBounds.bottom) shouldBe true
        }
        withClue("value top ${valueBounds.top} must sit below title and badge") {
            (valueBounds.top >= titleBounds.bottom && valueBounds.top >= badgeBounds.bottom) shouldBe true
        }

        mergedTexts(TAG_VALUE) shouldBe listOf(TITLE, BADGE_LABEL, VALUE, SUBTITLE)

        composeRule.onNodeWithTag(TAG_VALUE).performClick()
        composeRule.runOnIdle {
            upgrades shouldBe 1
            clicks shouldBe 0
        }
    }

    @Test
    fun `disabled value is dimmed tertiary labelLarge`() {
        var expectedColor = Color.Unspecified
        var expectedStyle = TextStyle.Default
        composeRule.setContent {
            PreviewWrapper {
                expectedColor = MaterialTheme.colorScheme.tertiary
                expectedStyle = MaterialTheme.typography.labelLarge
                Container {
                    SettingsPreferenceItem(
                        modifier = Modifier.testTag(TAG_VALUE),
                        title = TITLE,
                        subtitle = SUBTITLE,
                        value = VALUE,
                        onClick = {},
                    )
                    SettingsPreferenceItem(
                        modifier = Modifier.testTag(TAG_DISABLED),
                        title = TITLE,
                        subtitle = SUBTITLE,
                        value = VALUE,
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }

        val enabledStyle = value(TAG_VALUE).textLayoutResult().layoutInput.style
        enabledStyle.fontSize shouldBe expectedStyle.fontSize
        enabledStyle.fontWeight shouldBe expectedStyle.fontWeight
        enabledStyle.color shouldBe expectedColor.copy(alpha = 1f)

        val disabledStyle = value(TAG_DISABLED).textLayoutResult().layoutInput.style
        disabledStyle.fontSize shouldBe expectedStyle.fontSize
        disabledStyle.fontWeight shouldBe expectedStyle.fontWeight
        disabledStyle.color shouldBe expectedColor.copy(alpha = 0.5f)
    }

    @Test
    fun `large font scale keeps the value below the title`() {
        composeRule.setContent {
            PreviewWrapper {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    Container {
                        SettingsPreferenceItem(
                            modifier = Modifier.testTag(TAG_VALUE),
                            title = TITLE,
                            subtitle = SUBTITLE,
                            value = VALUE,
                            onClick = {},
                        )
                    }
                }
            }
        }

        val titleBounds = title(TAG_VALUE).getUnclippedBoundsInRoot()
        val valueBounds = value(TAG_VALUE).getUnclippedBoundsInRoot()

        withClue("value top ${valueBounds.top} must sit below title bottom ${titleBounds.bottom}") {
            (valueBounds.top >= titleBounds.bottom) shouldBe true
        }
    }

    @Test
    fun `RTL keeps the value below the title and start-aligned`() {
        composeRule.setContent {
            PreviewWrapper {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Container {
                        SettingsPreferenceItem(
                            modifier = Modifier.testTag(TAG_VALUE),
                            title = TITLE,
                            subtitle = SUBTITLE,
                            value = VALUE,
                            onClick = {},
                        )
                    }
                }
            }
        }

        val titleBounds = title(TAG_VALUE).getUnclippedBoundsInRoot()
        val valueBounds = value(TAG_VALUE).getUnclippedBoundsInRoot()

        withClue("value top ${valueBounds.top} must sit below title bottom ${titleBounds.bottom}") {
            (valueBounds.top >= titleBounds.bottom) shouldBe true
        }
        withClue("value right ${valueBounds.right} must align with title right ${titleBounds.right}") {
            (abs((valueBounds.right - titleBounds.right).value) < 1f) shouldBe true
        }
    }

    @Composable
    private fun Container(content: @Composable () -> Unit) {
        Column(modifier = Modifier.requiredWidth(CONTAINER_WIDTH)) { content() }
    }

    private fun title(rowTag: String): SemanticsNodeInteraction = composeRule.onNode(
        hasText(TITLE) and hasAnyAncestor(hasTestTag(rowTag)),
        useUnmergedTree = true,
    )

    private fun value(rowTag: String): SemanticsNodeInteraction = composeRule.onNode(
        hasText(VALUE) and hasAnyAncestor(hasTestTag(rowTag)),
        useUnmergedTree = true,
    )

    private fun SemanticsNodeInteraction.textLayoutResult(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first()
    }

    private fun mergedTexts(rowTag: String): List<String> = composeRule
        .onNodeWithTag(rowTag)
        .fetchSemanticsNode()
        .config[SemanticsProperties.Text]
        .map { it.text }

    private val DpRect.widthDp: Dp get() = right - left

    companion object {
        private val CONTAINER_WIDTH = 360.dp
        private const val TAG_PLAIN = "row-plain"
        private const val TAG_VALUE = "row-value"
        private const val TAG_DISABLED = "row-disabled"
        private const val TITLE = "Wykrywanie systemu operacyjnego"
        private const val VALUE = "Automatyczne (domyślnie)"
        private const val SUBTITLE = "Automatyzacja oparta na usłudze ułatwień dostępu może się nie " +
            "powieść, jeśli SD Maid nie wykryje poprawnie systemu operacyjnego."
        private const val BADGE_LABEL = "Ulepszenie do wersji Pro"
    }
}
