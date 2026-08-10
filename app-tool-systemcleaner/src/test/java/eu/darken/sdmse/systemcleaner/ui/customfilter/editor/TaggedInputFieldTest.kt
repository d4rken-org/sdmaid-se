package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.common.sieve.SieveCriterium
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.systemcleaner.R as SystemCleanerR

@OptIn(ExperimentalTestApi::class)
class TaggedInputFieldTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val downloads = SegmentCriterium(
        segments = listOf("Downloads"),
        mode = SegmentCriterium.Mode.Contain(allowPartial = true),
    )

    /** Which section the field under test renders — drives the section-specific add button. */
    private var renderedSection: CriteriaSection = CriteriaSection.PATH

    @Test
    fun `tapping the chip body opens the criterium editor`() {
        renderField(tags = listOf(downloads))

        tapChipBody(downloads)

        composeRule
            .onNodeWithText(string(CommonR.string.general_edit_action))
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag(CRITERIUM_EDITOR_INPUT_TEST_TAG)
            .assertTextContains("Downloads")
    }

    @Test
    fun `tapping the leading icon opens the matching mode dialog`() {
        renderField(tags = listOf(downloads))

        tapModeTarget(downloads)

        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_label))
            .assertIsDisplayed()
        // Not the editor: the icon is a shortcut, not a detour.
        composeRule
            .onAllNodesWithText(string(CommonR.string.general_edit_action))
            .fetchSemanticsNodes().size shouldBe 0
    }

    @Test
    fun `selecting a mode via the leading icon swaps the criterium`() {
        val swaps = mutableListOf<Pair<SieveCriterium, SieveCriterium>>()
        renderField(
            tags = listOf(downloads),
            onSwap = { old, new -> swaps += old to new },
        )

        tapModeTarget(downloads)
        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_end_label))
            .performClick()

        composeRule.runOnIdle {
            val (old, new) = swaps.single()
            old shouldBe downloads
            new shouldBe SegmentCriterium(
                segments = listOf("Downloads"),
                mode = SegmentCriterium.Mode.End(allowPartial = true),
            )
        }
    }

    @Test
    fun `tapping the remove icon removes the chip without opening a dialog`() {
        val removed = mutableListOf<SieveCriterium>()
        // Static tags: the chip stays rendered, so a tap leaking to the chip would surface as a dialog.
        renderField(
            tags = listOf(downloads),
            onRemove = { removed += it },
        )

        removeNode(downloads).performClick()

        composeRule.runOnIdle { removed shouldBe listOf(downloads) }
        composeRule
            .onAllNodesWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_label))
            .fetchSemanticsNodes().size shouldBe 0
        composeRule
            .onAllNodesWithText(string(CommonR.string.general_edit_action))
            .fetchSemanticsNodes().size shouldBe 0
    }

    @Test
    fun `a name-criterium chip opens the name comparison mode dialog`() {
        val receipt = NameCriterium(name = "receipt", mode = NameCriterium.Mode.Contain())
        renderField(type = TagType.NAME, section = CriteriaSection.NAME, tags = listOf(receipt))

        tapModeTarget(receipt)

        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_label))
            .assertIsDisplayed()
    }

    @Test
    fun `the chip exposes a Button role`() {
        renderField(tags = listOf(downloads))

        val chip = chipNode(downloads).fetchSemanticsNode()

        chip.config[SemanticsProperties.Role] shouldBe Role.Button
    }

    @Test
    fun `a criteria chip is no taller than the add chip`() {
        renderField(tags = listOf(downloads))

        // Both heights come from the measured layout, not from dp constants: what matters is that
        // the rows stay flush, whatever the chip defaults happen to be. A remove button that takes
        // its size from LocalMinimumInteractiveComponentSize instead of a fixed 24dp inflates the
        // whole chip and makes the criteria row visibly taller than the Add chip next to it.
        val chipHeight = chipNode(downloads).fetchSemanticsNode().size.height
        val addHeight = addNode().fetchSemanticsNode().size.height

        withClue("criteria chip height $chipHeight vs add chip height $addHeight") {
            (chipHeight <= addHeight) shouldBe true
        }
    }

    @Test
    fun `a short chip label is padded out to a three character minimum`() {
        val oneChar = SegmentCriterium(listOf("a"), SegmentCriterium.Mode.Contain(allowPartial = true))
        val threeChar = SegmentCriterium(listOf("abc"), SegmentCriterium.Mode.Contain(allowPartial = true))
        renderField(tags = listOf(oneChar, threeChar, downloads))

        // A single-character entry renders as a sliver between the two icons and reads as broken
        // next to its neighbours, so its label is floored at the width of three characters.
        val oneCharWidth = labelWidth(oneChar)
        val threeCharWidth = labelWidth(threeChar)
        val longWidth = labelWidth(downloads)

        withClue("1-char label $oneCharWidth vs 3-char label $threeCharWidth") {
            (oneCharWidth >= threeCharWidth) shouldBe true
        }
        // The floor is a minimum, not a fixed width: longer labels still size to their content.
        withClue("long label $longWidth vs 3-char label $threeCharWidth") {
            (longWidth > threeCharWidth) shouldBe true
        }
    }

    @Test
    fun `the add button opens the editor in create mode`() {
        renderField(tags = listOf(downloads))

        tapAdd()

        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_criterium_create_title))
            .assertIsDisplayed()
        // Empty draft: nothing to save yet.
        saveNode().assertIsNotEnabled()
    }

    @Test
    fun `create then mode then save adds instead of swapping`() {
        val added = mutableListOf<SieveCriterium>()
        val swaps = mutableListOf<Pair<SieveCriterium, SieveCriterium>>()
        renderField(
            tags = listOf(downloads),
            onAdd = { added += it },
            onSwap = { old, new -> swaps += old to new },
        )

        tapAdd()
        typeCriterium("Pictures")
        tapModeRow()
        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_end_label))
            .performClick()
        // Back in the editor: the draft survived the detour.
        composeRule.onNodeWithTag(CRITERIUM_EDITOR_INPUT_TEST_TAG).assertTextContains("Pictures")
        tapSave()

        composeRule.runOnIdle {
            swaps shouldBe emptyList()
            added shouldBe listOf(
                SegmentCriterium(listOf("Pictures"), SegmentCriterium.Mode.End(allowPartial = true)),
            )
        }
    }

    @Test
    fun `editing then mode then save swaps the original entry`() {
        val added = mutableListOf<SieveCriterium>()
        val swaps = mutableListOf<Pair<SieveCriterium, SieveCriterium>>()
        renderField(
            tags = listOf(downloads),
            onAdd = { added += it },
            onSwap = { old, new -> swaps += old to new },
        )

        tapChipBody(downloads)
        typeCriterium("Pictures")
        tapModeRow()
        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_equal_label))
            .performClick()
        tapSave()

        composeRule.runOnIdle {
            added shouldBe emptyList()
            val (old, new) = swaps.single()
            // The ORIGINAL chip, not the draft — swapPreservingOrder silently no-ops on a stranger.
            old shouldBe downloads
            new shouldBe SegmentCriterium(listOf("Pictures"), SegmentCriterium.Mode.Equal())
        }
    }

    @Test
    fun `cancelling the mode dialog keeps the draft and the previous mode`() {
        val added = mutableListOf<SieveCriterium>()
        renderField(tags = emptyList(), onAdd = { added += it })

        tapAdd()
        typeCriterium("Pictures")
        tapModeRow()
        composeRule.onNodeWithText(string(CommonR.string.general_cancel_action)).performClick()

        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_criterium_create_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(CRITERIUM_EDITOR_INPUT_TEST_TAG).assertTextContains("Pictures")
        tapSave()

        composeRule.runOnIdle {
            added shouldBe listOf(
                SegmentCriterium(listOf("Pictures"), SegmentCriterium.Mode.Contain(allowPartial = true)),
            )
        }
    }

    @Test
    fun `an exact duplicate blocks saving but the same text with another mode does not`() {
        val swaps = mutableListOf<Pair<SieveCriterium, SieveCriterium>>()
        val other = SegmentCriterium(listOf("Pictures"), SegmentCriterium.Mode.Contain(allowPartial = true))
        renderField(
            tags = listOf(downloads, other),
            onSwap = { old, new -> swaps += old to new },
        )

        tapChipBody(downloads)
        typeCriterium("Pictures")

        // Identical text AND mode as the sibling -> exact duplicate.
        saveNode().assertIsNotEnabled()
        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_criterium_duplicate_error))
            .assertIsDisplayed()

        // Same text, different mode -> a legitimately distinct criterium.
        tapModeRow()
        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_end_label))
            .performClick()
        saveNode().assertIsEnabled()
        tapSave()

        composeRule.runOnIdle {
            val (old, new) = swaps.single()
            old shouldBe downloads
            new shouldBe SegmentCriterium(listOf("Pictures"), SegmentCriterium.Mode.End(allowPartial = true))
        }
    }

    @Test
    fun `a mode that would collide with a sibling is disabled`() {
        val pictures = SegmentCriterium(listOf("Pictures"), SegmentCriterium.Mode.Contain(allowPartial = true))
        val picturesEqual = SegmentCriterium(listOf("Pictures"), SegmentCriterium.Mode.Equal())
        renderField(tags = listOf(pictures, picturesEqual))

        tapModeTarget(pictures)

        // Switching Contain -> Equal would produce an exact duplicate, which the criteria set
        // collapses: one chip would vanish with no dialog left to warn in.
        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_equal_label))
            .assertIsNotEnabled()
        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_end_label))
            .assertIsEnabled()
    }

    @Test
    fun `a colliding mode is blocked for an entry whose text is not normalised`() {
        // Surrounding whitespace survives an import: rebuilding the candidate from the chip text
        // trims it away, so the collision check would look for "foo" while the save writes " foo ".
        val padded = NameCriterium(name = " foo ", mode = NameCriterium.Mode.Contain())
        val paddedEqual = NameCriterium(name = " foo ", mode = NameCriterium.Mode.Equal())
        val swaps = mutableListOf<Pair<SieveCriterium, SieveCriterium>>()
        renderField(
            type = TagType.NAME,
            section = CriteriaSection.NAME,
            tags = listOf(padded, paddedEqual),
            onSwap = { old, new -> swaps += old to new },
        )

        tapModeTarget(padded)

        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_equal_label))
            .assertIsNotEnabled()

        // The mode that IS offered saves exactly the criterium that was validated: untrimmed.
        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_end_label))
            .performClick()

        composeRule.runOnIdle {
            val (old, new) = swaps.single()
            old shouldBe padded
            new shouldBe NameCriterium(name = " foo ", mode = NameCriterium.Mode.End())
        }
    }

    @Test
    fun `tapping the mode row opens the mode dialog and returns with the draft intact`() {
        val added = mutableListOf<SieveCriterium>()
        renderField(tags = emptyList(), onAdd = { added += it })

        tapAdd()
        typeCriterium("Pictures")
        composeRule.onNodeWithTag(CRITERIUM_EDITOR_MODE_ROW_TEST_TAG).assertHasClickAction()
        tapModeRow()

        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_label))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_end_label))
            .performClick()

        // Back in the editor: same draft, and the row now displays the freshly picked mode.
        composeRule.onNodeWithTag(CRITERIUM_EDITOR_INPUT_TEST_TAG).assertTextContains("Pictures")
        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_end_label))
            .assertIsDisplayed()
        tapSave()

        composeRule.runOnIdle {
            added shouldBe listOf(
                SegmentCriterium(listOf("Pictures"), SegmentCriterium.Mode.End(allowPartial = true)),
            )
        }
    }

    @Test
    fun `the mode row has no click label while the draft is empty`() {
        renderField(tags = listOf(downloads))

        tapAdd()

        // Formatting the label with an empty draft yields "...for , currently: ..." — TalkBack would
        // read the dangling "for ,". No label means the row announces its own visible mode instead.
        modeRowClickLabel() shouldBe null
    }

    @Test
    fun `the mode row names the entry once the draft has text`() {
        renderField(tags = listOf(downloads))

        tapChipBody(downloads)

        modeRowClickLabel() shouldBe string(
            SystemCleanerR.string.systemcleaner_customfilter_editor_criterium_mode_x_action,
            "Downloads",
            string(criteriumModeLabelRes(downloads)),
        )
    }

    @Test
    fun `typing one character at a time keeps the caret at the end`() {
        renderField(tags = emptyList())

        tapAdd()
        // One call per character: a per-keystroke recomposition that drops focus or resets the
        // selection only shows up between keystrokes, never in a single performTextReplacement.
        criteriumInput().performTextInput("a")
        criteriumInput().performTextInput("b")
        criteriumInput().performTextInput("c")

        criteriumText() shouldBe "abc"
        criteriumSelection() shouldBe TextRange(3)
    }

    @Test
    fun `typing on a hardware keyboard keeps the caret at the end`() {
        renderField(tags = emptyList())

        tapAdd()
        // The reporter typed on the emulator's keyboard: key events go through the focused node,
        // so a focus loss between keystrokes silently swallows the rest of the word.
        criteriumInput().performKeyInput { pressKey(Key.A) }
        criteriumInput().performKeyInput { pressKey(Key.B) }
        criteriumInput().performKeyInput { pressKey(Key.C) }

        criteriumText() shouldBe "abc"
        criteriumSelection() shouldBe TextRange(3)
    }

    @Test
    fun `clearing the text while editing turns the positive action into Remove`() {
        val removed = mutableListOf<SieveCriterium>()
        val added = mutableListOf<SieveCriterium>()
        val swaps = mutableListOf<Pair<SieveCriterium, SieveCriterium>>()
        renderField(
            tags = listOf(downloads),
            onAdd = { added += it },
            onRemove = { removed += it },
            onSwap = { old, new -> swaps += old to new },
        )

        tapChipBody(downloads)
        criteriumInput().performTextClearance()

        composeRule.onNodeWithText(string(CommonR.string.general_remove_action)).performClick()

        composeRule.runOnIdle {
            removed shouldBe listOf(downloads)
            added shouldBe emptyList()
            swaps shouldBe emptyList()
        }
    }

    @Test
    fun `an empty draft in create mode keeps a disabled Save`() {
        renderField(tags = listOf(downloads))

        tapAdd()

        // Nothing to remove when nothing exists yet — the create flow keeps its plain Save.
        saveNode().assertIsNotEnabled()
        composeRule
            .onAllNodesWithText(string(CommonR.string.general_remove_action))
            .fetchSemanticsNodes().size shouldBe 0
    }

    @Test
    fun `an unchanged edit offers Close and neither adds nor swaps`() {
        val added = mutableListOf<SieveCriterium>()
        val swaps = mutableListOf<Pair<SieveCriterium, SieveCriterium>>()
        val removed = mutableListOf<SieveCriterium>()
        renderField(
            tags = listOf(downloads),
            onAdd = { added += it },
            onRemove = { removed += it },
            onSwap = { old, new -> swaps += old to new },
        )

        tapChipBody(downloads)

        composeRule.onNodeWithText(string(CommonR.string.general_close_action)).performClick()

        composeRule.runOnIdle {
            added shouldBe emptyList()
            swaps shouldBe emptyList()
            removed shouldBe emptyList()
        }
        // Dismissed, not left open.
        composeRule
            .onAllNodesWithText(string(CommonR.string.general_edit_action))
            .fetchSemanticsNodes().size shouldBe 0
    }

    @Test
    fun `changing the text of an existing entry restores Save`() {
        val swaps = mutableListOf<Pair<SieveCriterium, SieveCriterium>>()
        renderField(tags = listOf(downloads), onSwap = { old, new -> swaps += old to new })

        tapChipBody(downloads)
        typeCriterium("Pictures")

        composeRule
            .onAllNodesWithText(string(CommonR.string.general_close_action))
            .fetchSemanticsNodes().size shouldBe 0
        tapSave()

        composeRule.runOnIdle {
            val (old, new) = swaps.single()
            old shouldBe downloads
            new shouldBe SegmentCriterium(listOf("Pictures"), SegmentCriterium.Mode.Contain(allowPartial = true))
        }
    }

    @Test
    fun `changing only the mode of an existing entry restores Save`() {
        val swaps = mutableListOf<Pair<SieveCriterium, SieveCriterium>>()
        renderField(tags = listOf(downloads), onSwap = { old, new -> swaps += old to new })

        tapChipBody(downloads)
        // Same text, different mode: still a real edit, so Close would strand the change.
        tapModeRow()
        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_end_label))
            .performClick()

        composeRule
            .onAllNodesWithText(string(CommonR.string.general_close_action))
            .fetchSemanticsNodes().size shouldBe 0
        tapSave()

        composeRule.runOnIdle {
            val (old, new) = swaps.single()
            old shouldBe downloads
            new shouldBe SegmentCriterium(listOf("Downloads"), SegmentCriterium.Mode.End(allowPartial = true))
        }
    }

    @Test
    fun `dpad down moves focus off the keyword field`() {
        renderField(tags = listOf(downloads))

        tapChipBody(downloads)
        criteriumInput().assertIsFocused()

        // A remote has no TAB key, and the field now holds the initial focus. Without an explicit
        // escape it eats the vertical keys for caret movement and Save is unreachable on TV.
        criteriumInput().performKeyInput { pressKey(Key.DirectionDown) }

        criteriumInput().assertIsNotFocused()
    }

    @Test
    fun `a whitespace-only entry can be saved and keeps its exact value`() {
        val added = mutableListOf<SieveCriterium>()
        renderField(
            type = TagType.NAME,
            section = CriteriaSection.NAME,
            tags = emptyList(),
            onAdd = { added += it },
        )

        tapAdd()
        typeCriterium("  ")

        // A name criterium of " " with *contains* matches every file whose name has a space in it,
        // and there is no other way to express that.
        saveNode().assertIsEnabled()
        tapSave()

        composeRule.runOnIdle {
            added shouldBe listOf(NameCriterium("  ", NameCriterium.Mode.Contain()))
        }
    }

    @Test
    fun `surrounding whitespace is still trimmed off a normal entry`() {
        val added = mutableListOf<SieveCriterium>()
        renderField(tags = emptyList(), onAdd = { added += it })

        tapAdd()
        typeCriterium("Downloads ")
        tapSave()

        // Only an all-whitespace value is kept verbatim; stray padding around real text is noise.
        composeRule.runOnIdle {
            added shouldBe listOf(
                SegmentCriterium(listOf("Downloads"), SegmentCriterium.Mode.Contain(allowPartial = true)),
            )
        }
    }

    @Test
    fun `whitespace chips render their count so they stay legible and distinct`() {
        val oneSpace = NameCriterium(" ", NameCriterium.Mode.Contain())
        val twoSpaces = NameCriterium("  ", NameCriterium.Mode.Contain())
        renderField(type = TagType.NAME, section = CriteriaSection.NAME, tags = listOf(oneSpace, twoSpaces))

        // Rendering the raw value would give two identically blank chips for two distinct criteria.
        composeRule.onNodeWithText(whitespaceLabel(1)).assertIsDisplayed()
        composeRule.onNodeWithText(whitespaceLabel(2)).assertIsDisplayed()
        whitespaceLabel(1) shouldNotBe whitespaceLabel(2)
    }

    @Test
    fun `a whitespace chip's edit and remove labels use the count`() {
        val twoSpaces = NameCriterium("  ", NameCriterium.Mode.Contain())
        renderField(type = TagType.NAME, section = CriteriaSection.NAME, tags = listOf(twoSpaces))

        // Built from the raw value these would be spoken as a dangling "Edit  " / "Remove  ".
        chipNode(twoSpaces).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label shouldBe
            string(CommonR.string.general_edit_x_action, whitespaceLabel(2))
        removeNode(twoSpaces).assertIsDisplayed()
    }

    @Test
    fun `editing a whitespace chip puts the raw whitespace back in the field`() {
        val twoSpaces = NameCriterium("  ", NameCriterium.Mode.Contain())
        renderField(type = TagType.NAME, section = CriteriaSection.NAME, tags = listOf(twoSpaces))

        tapChipBody(twoSpaces)

        // The count label is display only — the editor has to hand back the actual characters.
        criteriumText() shouldBe "  "
    }

    @Test
    fun `name input strips path separators`() {
        val added = mutableListOf<SieveCriterium>()
        renderField(
            type = TagType.NAME,
            section = CriteriaSection.NAME,
            tags = emptyList(),
            onAdd = { added += it },
        )

        tapAdd()
        typeCriterium("a/b/c")
        tapSave()

        composeRule.runOnIdle {
            added shouldBe listOf(NameCriterium("abc", NameCriterium.Mode.Contain()))
        }
    }

    @Test
    fun `the open editor survives a state restoration`() {
        val restorationTester = StateRestorationTester(composeRule)
        val added = mutableListOf<SieveCriterium>()
        restorationTester.setContent {
            PreviewWrapper {
                FieldUnderTest(tags = mutableStateListOf(downloads), onAdd = { added += it })
            }
        }

        tapChipBody(downloads)
        typeCriterium("Pictures")
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule
            .onNodeWithText(string(CommonR.string.general_edit_action))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(CRITERIUM_EDITOR_INPUT_TEST_TAG).assertTextContains("Pictures")
    }

    @Test
    fun `the open mode dialog survives a state restoration and returns to the editor`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            PreviewWrapper {
                FieldUnderTest(tags = mutableStateListOf(downloads))
            }
        }

        tapChipBody(downloads)
        typeCriterium("Pictures")
        tapModeRow()
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule
            .onNodeWithText(string(SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_label))
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(CommonR.string.general_cancel_action)).performClick()
        composeRule.onNodeWithTag(CRITERIUM_EDITOR_INPUT_TEST_TAG).assertTextContains("Pictures")
    }

    private fun string(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args)

    /** "N spaces" for an all-whitespace value, the raw value otherwise — what the chip renders. */
    private fun whitespaceLabel(count: Int): String = context.resources.getQuantityString(
        SystemCleanerR.plurals.systemcleaner_customfilter_editor_criterium_whitespace_label,
        count,
        count,
    )

    private fun displayLabel(criterium: SieveCriterium): String {
        val raw = criteriumValue(criterium)
        return if (raw.isNotEmpty() && raw.isBlank()) whitespaceLabel(raw.length) else raw
    }

    private fun chipNode(criterium: SieveCriterium) = composeRule
        .onAllNodes(hasClickAction() and hasAnyDescendant(hasText(displayLabel(criterium))), useUnmergedTree = true)
        .onFirst()

    private fun modeNode(criterium: SieveCriterium) = composeRule.onNodeWithContentDescription(
        string(
            SystemCleanerR.string.systemcleaner_customfilter_editor_criterium_mode_x_action,
            displayLabel(criterium),
            string(criteriumModeLabelRes(criterium)),
        ),
        useUnmergedTree = true,
    )

    private fun removeNode(criterium: SieveCriterium) = composeRule.onNodeWithContentDescription(
        string(CommonR.string.general_remove_x_action, displayLabel(criterium)),
        useUnmergedTree = true,
    )

    private fun saveNode() = composeRule.onNodeWithText(string(CommonR.string.general_save_action))

    /** The accessibility click label of the editor's mode row, or null if it exposes none. */
    private fun modeRowClickLabel(): String? = composeRule
        .onNodeWithTag(CRITERIUM_EDITOR_MODE_ROW_TEST_TAG)
        .fetchSemanticsNode()
        .config
        .getOrNull(SemanticsActions.OnClick)
        ?.label

    /**
     * Real touch injection (never a semantics action): what has to hold is that a pointer landing
     * on the chip body reaches the editor. The target is computed from the measured gap between
     * the mode target and the remove button, and the assertion that a gap exists at all is the
     * point — Robolectric's stub text metrics make labels a few px wide, which is the worst case.
     */
    private fun tapChipBody(criterium: SieveCriterium) {
        val chipBounds = chipNode(criterium).fetchSemanticsNode().boundsInRoot
        val modeBounds = modeNode(criterium).fetchSemanticsNode().touchBoundsInRoot
        val removeBounds = removeNode(criterium).fetchSemanticsNode().touchBoundsInRoot
        if (modeBounds.right >= removeBounds.left) {
            throw AssertionError(
                "No tappable chip body: mode target ends at ${modeBounds.right}, " +
                    "remove target starts at ${removeBounds.left}",
            )
        }
        val x = (modeBounds.right + removeBounds.left) / 2f - chipBounds.left
        chipNode(criterium).performTouchInput { click(Offset(x, chipBounds.height / 2f)) }
    }

    private fun tapModeTarget(criterium: SieveCriterium) = modeNode(criterium).performTouchInput {
        click(center)
    }

    /** Width of the chip's own label node, i.e. excluding the mode target and remove button. */
    private fun labelWidth(criterium: SieveCriterium) = composeRule
        .onNodeWithText(criteriumValue(criterium), useUnmergedTree = true)
        .fetchSemanticsNode()
        .size
        .width

    private fun addNode() = composeRule
        .onNodeWithContentDescription(string(criteriumAddDescriptionRes(renderedSection)))

    private fun tapAdd() = addNode().performClick()

    private fun criteriumInput() = composeRule.onNodeWithTag(CRITERIUM_EDITOR_INPUT_TEST_TAG)

    private fun criteriumText(): String = criteriumInput()
        .fetchSemanticsNode()
        .config[SemanticsProperties.EditableText]
        .text

    private fun criteriumSelection(): TextRange = criteriumInput()
        .fetchSemanticsNode()
        .config[SemanticsProperties.TextSelectionRange]

    private fun typeCriterium(text: String) = composeRule
        .onNodeWithTag(CRITERIUM_EDITOR_INPUT_TEST_TAG)
        .performTextReplacement(text)

    private fun tapModeRow() = composeRule
        .onNodeWithTag(CRITERIUM_EDITOR_MODE_ROW_TEST_TAG)
        .performClick()

    private fun tapSave() = saveNode().performClick()

    @Composable
    private fun FieldUnderTest(
        tags: List<SieveCriterium>,
        type: TagType = TagType.SEGMENTS,
        section: CriteriaSection = renderedSection,
        onAdd: (SieveCriterium) -> Unit = {},
        onRemove: (SieveCriterium) -> Unit = {},
        onSwap: (old: SieveCriterium, new: SieveCriterium) -> Unit = { _, _ -> },
    ) = TaggedInputField(
        type = type,
        section = section,
        hint = "Criteria",
        tags = tags,
        onAdd = onAdd,
        onRemove = onRemove,
        onSwap = onSwap,
    )

    private fun renderField(
        tags: List<SieveCriterium>,
        type: TagType = TagType.SEGMENTS,
        section: CriteriaSection = CriteriaSection.PATH,
        onAdd: (SieveCriterium) -> Unit = {},
        onRemove: (SieveCriterium) -> Unit = {},
        onSwap: (old: SieveCriterium, new: SieveCriterium) -> Unit = { _, _ -> },
    ) {
        renderedSection = section
        composeRule.setContent {
            PreviewWrapper {
                FieldUnderTest(
                    tags = tags,
                    type = type,
                    section = section,
                    onAdd = onAdd,
                    onRemove = onRemove,
                    onSwap = onSwap,
                )
            }
        }
    }
}
