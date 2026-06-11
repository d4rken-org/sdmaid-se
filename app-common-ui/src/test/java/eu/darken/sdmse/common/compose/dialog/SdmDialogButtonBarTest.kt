package eu.darken.sdmse.common.compose.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class SdmDialogButtonBarTest : BaseComposeRobolectricTest() {

    private val positiveLabel = "Delete"
    private val negativeLabel = "Cancel"
    private val neutralLabel = "Details"

    private fun setBar(
        containerWidth: Int? = null,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        positive: SdmDialogAction = SdmDialogAction(label = positiveLabel, onClick = {}),
        negative: SdmDialogAction? = SdmDialogAction(label = negativeLabel, onClick = {}),
        neutral: SdmDialogAction? = SdmDialogAction(label = neutralLabel, onClick = {}),
    ) {
        composeRule.setContent {
            PreviewWrapper {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    val modifier = containerWidth?.let { Modifier.width(it.dp) } ?: Modifier
                    Box(modifier = modifier) {
                        SdmDialogButtonBar(
                            positive = positive,
                            negative = negative,
                            neutral = neutral,
                        )
                    }
                }
            }
        }
    }

    private fun leftOf(label: String) =
        composeRule.onNodeWithText(label).fetchSemanticsNode().positionInRoot.x

    private fun topOf(label: String) =
        composeRule.onNodeWithText(label).fetchSemanticsNode().positionInRoot.y

    private fun rightOf(label: String) = composeRule.onNodeWithText(label).fetchSemanticsNode().let {
        it.positionInRoot.x + it.size.width
    }

    @Test
    fun `horizontal mode places neutral leading and negative-positive trailing`() {
        setBar(containerWidth = 400)

        val neutralX = leftOf(neutralLabel)
        val negativeX = leftOf(negativeLabel)
        val positiveX = leftOf(positiveLabel)

        assertTrue("neutral ($neutralX) should sit left of negative ($negativeX)", neutralX < negativeX)
        assertTrue("negative ($negativeX) should sit left of positive ($positiveX)", negativeX < positiveX)
        assertEquals("buttons should share a row", topOf(positiveLabel), topOf(negativeLabel), 1f)
    }

    @Test
    fun `narrow container stacks buttons vertically positive first`() {
        setBar(containerWidth = 120)

        val positiveY = topOf(positiveLabel)
        val negativeY = topOf(negativeLabel)
        val neutralY = topOf(neutralLabel)

        assertTrue("positive ($positiveY) should sit above negative ($negativeY)", positiveY < negativeY)
        assertTrue("negative ($negativeY) should sit above neutral ($neutralY)", negativeY < neutralY)
    }

    @Test
    fun `stacked buttons are end aligned`() {
        setBar(containerWidth = 120)

        val positiveRight = rightOf(positiveLabel)
        val negativeRight = rightOf(negativeLabel)
        val neutralRight = rightOf(neutralLabel)

        assertEquals("positive and negative right edges should align", positiveRight, negativeRight, 1f)
        assertEquals("negative and neutral right edges should align", negativeRight, neutralRight, 1f)
    }

    @Test
    fun `multi-word labels that only fit when wrapped still trigger stacking`() {
        composeRule.setContent {
            PreviewWrapper {
                Box(modifier = Modifier.width(140.dp)) {
                    SdmDialogButtonBar(
                        positive = SdmDialogAction(label = "Delete all selected files", onClick = {}),
                        negative = SdmDialogAction(label = "Cancel everything now", onClick = {}),
                        neutral = SdmDialogAction(label = "Show many more details", onClick = {}),
                    )
                }
            }
        }

        val positiveY = topOf("Delete all selected files")
        val negativeY = topOf("Cancel everything now")
        val neutralY = topOf("Show many more details")

        assertTrue("positive should sit above negative", positiveY < negativeY)
        assertTrue("negative should sit above neutral", negativeY < neutralY)
    }

    @Test
    fun `positive and neutral without negative keeps horizontal contract`() {
        setBar(containerWidth = 400, negative = null)

        composeRule.onAllNodesWithText(negativeLabel).assertCountEquals(0)
        val neutralX = leftOf(neutralLabel)
        val positiveX = leftOf(positiveLabel)
        assertTrue("neutral ($neutralX) should sit left of positive ($positiveX)", neutralX < positiveX)
    }

    @Test
    fun `positive only renders single button`() {
        setBar(containerWidth = 400, negative = null, neutral = null)

        composeRule.onNodeWithText(positiveLabel).assertIsDisplayed()
        composeRule.onAllNodesWithText(negativeLabel).assertCountEquals(0)
        composeRule.onAllNodesWithText(neutralLabel).assertCountEquals(0)
    }

    @Test
    fun `rtl horizontal mode mirrors placement`() {
        setBar(containerWidth = 400, layoutDirection = LayoutDirection.Rtl)

        val neutralX = leftOf(neutralLabel)
        val negativeX = leftOf(negativeLabel)
        val positiveX = leftOf(positiveLabel)

        assertTrue("positive ($positiveX) should sit left of negative ($negativeX) in RTL", positiveX < negativeX)
        assertTrue("negative ($negativeX) should sit left of neutral ($neutralX) in RTL", negativeX < neutralX)
    }

    @Test
    fun `disabled actions cannot be clicked`() {
        var positiveClicks = 0
        setBar(
            containerWidth = 400,
            positive = SdmDialogAction(label = positiveLabel, enabled = false) { positiveClicks++ },
        )

        composeRule.onNodeWithText(positiveLabel).assertIsNotEnabled()
        composeRule.onNodeWithText(negativeLabel).assertIsEnabled()
        composeRule.onNodeWithText(positiveLabel).performClick()
        composeRule.runOnIdle { assertEquals(0, positiveClicks) }
    }

    @Test
    fun `clicks invoke the matching callbacks in both layout modes`() {
        var positiveClicks = 0
        var negativeClicks = 0
        var neutralClicks = 0

        setBar(
            containerWidth = 120,
            positive = SdmDialogAction(label = positiveLabel) { positiveClicks++ },
            negative = SdmDialogAction(label = negativeLabel) { negativeClicks++ },
            neutral = SdmDialogAction(label = neutralLabel) { neutralClicks++ },
        )

        composeRule.onNodeWithText(positiveLabel).performClick()
        composeRule.onNodeWithText(neutralLabel).performClick()
        composeRule.runOnIdle {
            assertEquals(1, positiveClicks)
            assertEquals(0, negativeClicks)
            assertEquals(1, neutralClicks)
        }
    }

    @Test
    fun `initial focus defaults to the negative action when present`() {
        setBar(containerWidth = 400)

        composeRule.onNodeWithText(negativeLabel).assertIsFocused()
    }

    @Test
    fun `initial focus falls back to the positive action without a negative`() {
        setBar(containerWidth = 400, negative = null)

        composeRule.onNodeWithText(positiveLabel).assertIsFocused()
    }

    @Test
    fun `initialFocus flag overrides the safe default`() {
        setBar(
            containerWidth = 400,
            positive = SdmDialogAction(label = positiveLabel, initialFocus = true, onClick = {}),
        )

        composeRule.onNodeWithText(positiveLabel).assertIsFocused()
        composeRule.onNodeWithText(negativeLabel).assertIsNotFocused()
    }

    @Test
    fun `initialFocus flag can target the neutral action`() {
        setBar(
            containerWidth = 400,
            neutral = SdmDialogAction(label = neutralLabel, initialFocus = true, onClick = {}),
        )

        composeRule.onNodeWithText(neutralLabel).assertIsFocused()
    }

    @Test
    fun `initial focus applies in stacked mode too`() {
        setBar(containerWidth = 120)

        composeRule.onNodeWithText(negativeLabel).assertIsFocused()
    }
}
