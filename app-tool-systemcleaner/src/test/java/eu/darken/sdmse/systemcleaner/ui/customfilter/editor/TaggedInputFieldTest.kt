package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.input.TextFieldValue
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.common.sieve.SieveCriterium
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

@OptIn(ExperimentalTestApi::class)
class TaggedInputFieldTest : BaseComposeRobolectricTest() {

    private val downloads = SegmentCriterium(
        segments = listOf("Downloads"),
        mode = SegmentCriterium.Mode.Contain(allowPartial = true),
    )

    @Test
    fun `backspace pops the last chip and arrow keys then edit the draft in place`() {
        // Stateful tags so onRemove actually drops the chip and recomposes, mirroring production.
        val tags = mutableStateListOf<SieveCriterium>(downloads)
        composeRule.setContent {
            PreviewWrapper {
                TaggedInputField(
                    type = TagType.SEGMENTS,
                    hint = "Path",
                    tags = tags,
                    onAdd = { tags += it },
                    onRemove = { tags.remove(it) },
                    onModeChange = { _, _ -> },
                )
            }
        }

        val field = composeRule.onNodeWithTag(TAGGED_INPUT_FIELD_TEST_TAG)
        field.performClick()
        field.performKeyInput { pressKey(Key.Backspace) }

        // The chip is gone from state and its value is now the editable draft.
        composeRule.runOnIdle { tags.toList() shouldBe emptyList() }
        field.assertTextEquals("Downloads")

        // Caret started at the end; one left + typing must insert mid-string (not escape focus).
        field.performKeyInput { pressKey(Key.DirectionLeft) }
        field.assertIsFocused()
        field.performTextInput("X")
        field.assertTextEquals("DownloadXs")
    }

    @Test
    fun `losing focus while editing a popped chip re-commits it preserving the matching mode`() {
        val endChip = SegmentCriterium(
            segments = listOf("Pictures"),
            mode = SegmentCriterium.Mode.End(allowPartial = true),
        )
        val tags = mutableStateListOf<SieveCriterium>(endChip)
        val added = mutableListOf<SieveCriterium>()
        composeRule.setContent {
            PreviewWrapper {
                Column {
                    TaggedInputField(
                        type = TagType.SEGMENTS,
                        hint = "Path",
                        tags = tags,
                        onAdd = {
                            added += it
                            tags += it
                        },
                        onRemove = { tags.remove(it) },
                        onModeChange = { _, _ -> },
                    )
                    // A sibling focus target so we can pull focus away from the input.
                    BasicTextField(
                        value = TextFieldValue(""),
                        onValueChange = {},
                        modifier = Modifier.testTag("focus-sink"),
                    )
                }
            }
        }

        val field = composeRule.onNodeWithTag(TAGGED_INPUT_FIELD_TEST_TAG)
        field.performClick()
        field.performKeyInput { pressKey(Key.Backspace) }
        // Move focus away mid-edit — the draft must be re-committed, not silently dropped.
        composeRule.onNodeWithTag("focus-sink").performClick()

        composeRule.runOnIdle {
            val committed = added.single().shouldBeInstanceOf<SegmentCriterium>()
            committed.segments shouldBe listOf("Pictures")
            committed.mode.shouldBeInstanceOf<SegmentCriterium.Mode.End>()
        }
    }
}
