package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import eu.darken.sdmse.common.compose.icons.ApproximatelyEqual
import eu.darken.sdmse.common.compose.icons.Contain
import eu.darken.sdmse.common.compose.icons.ContainEnd
import eu.darken.sdmse.common.compose.icons.ContainStart
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.files.joinSegments
import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.common.sieve.SieveCriterium
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.systemcleaner.R as SystemCleanerR

internal enum class TagType {
    SEGMENTS,
    NAME,
}

/**
 * Which criteria list a chip row belongs to. [TagType] can't carry this: path criteria and
 * exclusions are both [TagType.SEGMENTS], so it can't tell an exclusion editor from a path editor.
 * [TagType] keeps driving criterium construction, this drives the user-facing wording.
 */
internal enum class CriteriaSection {
    PATH,
    NAME,
    EXCLUSION,
}

// The edit case reuses the plain "Edit": the section subtitle right below supplies the object, so
// the dialog reads "Edit / Path criteria". Creating keeps its own title — "New" alone is a badge,
// not a heading.
@StringRes
internal fun criteriumEditorTitleRes(isNew: Boolean): Int = if (isNew) {
    SystemCleanerR.string.systemcleaner_customfilter_editor_criterium_create_title
} else {
    CommonR.string.general_edit_action
}

/** Which list the editor is working on, shown as the dialog's subtitle — the section's own label. */
@StringRes
internal fun criteriumSectionLabelRes(section: CriteriaSection): Int = when (section) {
    CriteriaSection.PATH -> SystemCleanerR.string.systemcleaner_customfilter_editor_path_label
    CriteriaSection.NAME -> SystemCleanerR.string.systemcleaner_customfilter_editor_name_label
    CriteriaSection.EXCLUSION -> SystemCleanerR.string.systemcleaner_customfilter_editor_exclusions_label
}

@StringRes
internal fun criteriumAddDescriptionRes(section: CriteriaSection): Int = when (section) {
    CriteriaSection.PATH -> SystemCleanerR.string.systemcleaner_customfilter_editor_path_criterium_add_action
    CriteriaSection.NAME -> SystemCleanerR.string.systemcleaner_customfilter_editor_name_criterium_add_action
    CriteriaSection.EXCLUSION -> SystemCleanerR.string.systemcleaner_customfilter_editor_exclusion_criterium_add_action
}

private val SEGMENTS_DEFAULT_MODE = SegmentCriterium.Mode.Contain(allowPartial = true)
private val NAME_DEFAULT_MODE = NameCriterium.Mode.Contain()

internal fun inputTextToChipTag(input: String, type: TagType): SieveCriterium = when (type) {
    TagType.SEGMENTS -> SegmentCriterium(
        segments = input.toSegs(),
        mode = SEGMENTS_DEFAULT_MODE,
    )

    TagType.NAME -> NameCriterium(
        name = input,
        mode = NAME_DEFAULT_MODE,
    )
}

/** Mode a freshly created criterium starts out with. */
internal fun defaultModeFor(type: TagType): SieveCriterium.Mode = when (type) {
    TagType.SEGMENTS -> SEGMENTS_DEFAULT_MODE
    TagType.NAME -> NAME_DEFAULT_MODE
}

/**
 * Builds a criterium from raw [input] text. When [basedOn] is supplied (i.e. the user is editing
 * an existing chip that was popped back into the input) its matching mode is preserved, instead of
 * resetting to the default Contain mode.
 */
internal fun inputTextToChipTag(input: String, type: TagType, basedOn: SieveCriterium?): SieveCriterium {
    val fresh = inputTextToChipTag(input, type)
    return if (basedOn != null) withMode(fresh, criteriumMode(basedOn)) else fresh
}

/**
 * Surrounding whitespace is noise the user did not mean to type — except when whitespace is ALL
 * they typed, which is a legitimate criterium: a name criterium of `" "` with *contains* matches
 * every file whose name has a space in it, and there is no other way to express that. Trimming it
 * to nothing is what made such entries impossible to create.
 */
internal fun normaliseCriteriumInput(input: String): String = if (input.isBlank()) input else input.trim()

/**
 * The single canonical way to turn editor input into a criterium: the same normalisation, the same
 * `/`-handling and the same mode for validation and for saving. Building it twice (once to check
 * for duplicates, once to emit) is how a validated candidate and a saved candidate drift apart.
 */
internal fun buildCriterium(input: String, type: TagType, mode: SieveCriterium.Mode): SieveCriterium =
    withMode(inputTextToChipTag(normaliseCriteriumInput(input), type), mode)

/**
 * What the user SEES for a criterium value. Identical to the stored value except for whitespace,
 * which is rendered as its own count ("2 spaces").
 *
 * Display only — the stored criterium keeps the exact whitespace, [criteriumValue] keeps returning
 * it raw (it seeds the editor's text field), and duplicate detection stays exact. Without this a
 * whitespace chip renders as a blank box that reads as a rendering fault, `" "` and `"  "` look
 * identical while being distinct criteria, and screen readers announce a dangling "Edit ".
 */
@Composable
internal fun criteriumDisplayText(value: String): String = when {
    value.isEmpty() || value.isNotBlank() -> value
    else -> pluralStringResource(
        SystemCleanerR.plurals.systemcleaner_customfilter_editor_criterium_whitespace_label,
        value.length,
        value.length,
    )
}

/**
 * Modes that [input] must not be switched to because an existing sibling criterium already uses
 * them. Switching anyway would produce an exact duplicate, and [swapPreservingOrder] collapses
 * duplicates — the chip would silently vanish.
 */
internal fun collidingModes(
    input: String,
    type: TagType,
    siblings: List<SieveCriterium>,
): Set<SieveCriterium.Mode> {
    // isEmpty, not isBlank: whitespace is a real value now, and skipping its collision check would
    // let two identical whitespace criteria collapse into one when a mode is switched.
    if (input.isEmpty() || siblings.isEmpty()) return emptySet()
    return availableModesFor(type)
        .map { it.first }
        .filter { mode -> siblings.contains(buildCriterium(input, type, mode)) }
        .toSet()
}

internal fun tagTypeOf(criterium: SieveCriterium): TagType = when (criterium) {
    is NameCriterium -> TagType.NAME
    is SegmentCriterium -> TagType.SEGMENTS
}

internal fun criteriumMode(criterium: SieveCriterium): SieveCriterium.Mode = when (criterium) {
    is NameCriterium -> criterium.mode
    is SegmentCriterium -> criterium.mode
}

/**
 * Removes `/` from a [TextFieldValue]'s text (name criteria cannot contain path separators) while
 * keeping the caret/selection anchored to the same logical position by subtracting the number of
 * removed separators before each endpoint. Returns the value unchanged when there is nothing to strip.
 */
internal fun stripSlashes(value: TextFieldValue): TextFieldValue {
    val original = value.text
    if (!original.contains('/')) return value
    val filtered = original.filterNot { it == '/' }
    fun mapOffset(offset: Int): Int {
        val clamped = offset.coerceIn(0, original.length)
        return (clamped - original.take(clamped).count { it == '/' }).coerceIn(0, filtered.length)
    }
    return TextFieldValue(
        text = filtered,
        selection = TextRange(mapOffset(value.selection.start), mapOffset(value.selection.end)),
    )
}

internal fun criteriumValue(criterium: SieveCriterium): String = when (criterium) {
    is NameCriterium -> criterium.name
    is SegmentCriterium -> criterium.segments.joinSegments()
}

internal fun criteriumIcon(criterium: SieveCriterium): ImageVector = modeIcon(criterium.mode)

internal fun modeIcon(mode: SieveCriterium.Mode): ImageVector = when (mode) {
    is NameCriterium.Mode.Contain -> SdmIcons.Contain
    is NameCriterium.Mode.End -> SdmIcons.ContainEnd
    is NameCriterium.Mode.Equal -> SdmIcons.ApproximatelyEqual
    is NameCriterium.Mode.Start -> SdmIcons.ContainStart

    is SegmentCriterium.Mode.Contain -> SdmIcons.Contain
    is SegmentCriterium.Mode.End -> SdmIcons.ContainEnd
    is SegmentCriterium.Mode.Equal -> SdmIcons.ApproximatelyEqual
    is SegmentCriterium.Mode.Start -> SdmIcons.ContainStart
    is SegmentCriterium.Mode.Ancestor -> throw IllegalArgumentException("Ancestor not supported")
    is SegmentCriterium.Mode.Specific -> throw IllegalArgumentException("Specific not supported")
}

@StringRes
internal fun criteriumModeLabelRes(criterium: SieveCriterium): Int = modeLabelRes(criterium.mode)

@StringRes
internal fun modeLabelRes(mode: SieveCriterium.Mode): Int = when (mode) {
    is NameCriterium.Mode.Start -> SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_start_label
    is NameCriterium.Mode.Contain -> SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_contains_label
    is NameCriterium.Mode.End -> SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_end_label
    is NameCriterium.Mode.Equal -> SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_equal_label

    is SegmentCriterium.Mode.Start -> SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_start_label
    is SegmentCriterium.Mode.Contain -> SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_contains_label
    is SegmentCriterium.Mode.End -> SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_end_label
    is SegmentCriterium.Mode.Equal -> SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_equal_label
    is SegmentCriterium.Mode.Ancestor -> throw IllegalArgumentException("Ancestor not supported")
    is SegmentCriterium.Mode.Specific -> throw IllegalArgumentException("Specific not supported")
}

@StringRes
internal fun modeSwitcherTitleRes(criterium: SieveCriterium): Int = modeSwitcherTitleRes(tagTypeOf(criterium))

@StringRes
internal fun modeSwitcherTitleRes(type: TagType): Int = when (type) {
    TagType.SEGMENTS -> SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_label
    TagType.NAME -> SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_label
}

internal fun availableModesFor(criterium: SieveCriterium): List<Pair<SieveCriterium.Mode, Int>> =
    availableModesFor(tagTypeOf(criterium))

internal fun availableModesFor(type: TagType): List<Pair<SieveCriterium.Mode, Int>> = when (type) {
    TagType.NAME -> listOf(
        NameCriterium.Mode.Start() to SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_start_label,
        NameCriterium.Mode.Contain() to SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_contains_label,
        NameCriterium.Mode.End() to SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_end_label,
        NameCriterium.Mode.Equal() to SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_equal_label,
    )

    TagType.SEGMENTS -> listOf(
        SegmentCriterium.Mode.Start(allowPartial = true) to SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_start_label,
        SegmentCriterium.Mode.Contain(allowPartial = true) to SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_contains_label,
        SegmentCriterium.Mode.End(allowPartial = true) to SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_end_label,
        SegmentCriterium.Mode.Equal() to SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_equal_label,
    )
}

internal fun withMode(criterium: SieveCriterium, newMode: SieveCriterium.Mode): SieveCriterium = when (criterium) {
    is NameCriterium -> {
        require(newMode is NameCriterium.Mode) { "Expected NameCriterium.Mode for NameCriterium" }
        criterium.copy(mode = newMode)
    }

    is SegmentCriterium -> {
        require(newMode is SegmentCriterium.Mode) { "Expected SegmentCriterium.Mode for SegmentCriterium" }
        criterium.copy(mode = newMode)
    }
}

/**
 * Replaces [old] with [new] in [set] while preserving the original index of [old].
 * If [new] already exists at a different index, the duplicate is dropped (the entry that landed
 * at the lower index wins) so the resulting set keeps its de-dup invariant.
 * If [old] is not in [set] the function returns the set unchanged.
 * If [set] is null it is treated as empty.
 */
internal fun swapPreservingOrder(
    set: Set<SieveCriterium>?,
    old: SieveCriterium,
    new: SieveCriterium,
): Set<SieveCriterium> {
    val source = set ?: emptySet()
    if (old !in source) return source.toCollection(LinkedHashSet())
    val result = LinkedHashSet<SieveCriterium>(source.size)
    for (entry in source) {
        when (entry) {
            old -> if (new !in result) result.add(new)
            new -> Unit
            else -> result.add(entry)
        }
    }
    return result
}
