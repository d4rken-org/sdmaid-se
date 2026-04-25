package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.darken.sdmse.common.files.joinSegments
import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.common.sieve.SieveCriterium
import eu.darken.sdmse.common.ui.R as UiR
import eu.darken.sdmse.systemcleaner.R as SystemCleanerR

internal enum class TagType {
    SEGMENTS,
    NAME,
}

internal fun inputTextToChipTag(input: String, type: TagType): SieveCriterium = when (type) {
    TagType.SEGMENTS -> SegmentCriterium(
        segments = input.toSegs(),
        mode = SegmentCriterium.Mode.Contain(allowPartial = true),
    )

    TagType.NAME -> NameCriterium(
        name = input,
        mode = NameCriterium.Mode.Contain(),
    )
}

internal fun criteriumValue(criterium: SieveCriterium): String = when (criterium) {
    is NameCriterium -> criterium.name
    is SegmentCriterium -> criterium.segments.joinSegments()
}

@DrawableRes
internal fun criteriumIconRes(criterium: SieveCriterium): Int = when (criterium) {
    is NameCriterium -> when (criterium.mode) {
        is NameCriterium.Mode.Contain -> UiR.drawable.ic_contain_24
        is NameCriterium.Mode.End -> UiR.drawable.ic_contain_end_24
        is NameCriterium.Mode.Equal -> UiR.drawable.ic_approximately_equal_24
        is NameCriterium.Mode.Start -> UiR.drawable.ic_contain_start_24
    }

    is SegmentCriterium -> when (criterium.mode) {
        is SegmentCriterium.Mode.Contain -> UiR.drawable.ic_contain_24
        is SegmentCriterium.Mode.End -> UiR.drawable.ic_contain_end_24
        is SegmentCriterium.Mode.Equal -> UiR.drawable.ic_approximately_equal_24
        is SegmentCriterium.Mode.Start -> UiR.drawable.ic_contain_start_24
        is SegmentCriterium.Mode.Ancestor -> throw IllegalArgumentException("Ancestor not supported")
        is SegmentCriterium.Mode.Specific -> throw IllegalArgumentException("Specific not supported")
    }
}

@StringRes
internal fun criteriumModeLabelRes(criterium: SieveCriterium): Int = when (criterium) {
    is NameCriterium -> when (criterium.mode) {
        is NameCriterium.Mode.Start -> SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_start_label
        is NameCriterium.Mode.Contain -> SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_contains_label
        is NameCriterium.Mode.End -> SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_end_label
        is NameCriterium.Mode.Equal -> SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_equal_label
    }

    is SegmentCriterium -> when (criterium.mode) {
        is SegmentCriterium.Mode.Start -> SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_start_label
        is SegmentCriterium.Mode.Contain -> SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_contains_label
        is SegmentCriterium.Mode.End -> SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_end_label
        is SegmentCriterium.Mode.Equal -> SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_equal_label
        is SegmentCriterium.Mode.Ancestor -> throw IllegalArgumentException("Ancestor not supported")
        is SegmentCriterium.Mode.Specific -> throw IllegalArgumentException("Specific not supported")
    }
}

@StringRes
internal fun modeSwitcherTitleRes(criterium: SieveCriterium): Int = when (criterium) {
    is SegmentCriterium -> SystemCleanerR.string.systemcleaner_customfilter_editor_segments_matching_mode_label
    is NameCriterium -> SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_label
}

internal fun availableModesFor(criterium: SieveCriterium): List<Pair<SieveCriterium.Mode, Int>> = when (criterium) {
    is NameCriterium -> listOf(
        NameCriterium.Mode.Start() to SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_start_label,
        NameCriterium.Mode.Contain() to SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_contains_label,
        NameCriterium.Mode.End() to SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_end_label,
        NameCriterium.Mode.Equal() to SystemCleanerR.string.systemcleaner_customfilter_editor_name_matching_mode_equal_label,
    )

    is SegmentCriterium -> listOf(
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
