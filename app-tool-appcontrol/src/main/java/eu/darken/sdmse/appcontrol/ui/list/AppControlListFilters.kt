package eu.darken.sdmse.appcontrol.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Sort
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.KeyboardArrowDown
import androidx.compose.material.icons.twotone.KeyboardArrowUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.appcontrol.R
import eu.darken.sdmse.appcontrol.core.FilterSettings
import eu.darken.sdmse.appcontrol.core.SortSettings
import eu.darken.sdmse.common.R as CommonR

@Composable
internal fun AppControlFilterRow(
    activeTags: Set<FilterSettings.Tag>,
    sortNonDefault: Boolean,
    allowFilterActive: Boolean,
    onTagRemove: (FilterSettings.Tag) -> Unit,
    onAddTags: () -> Unit,
    onSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ordered = remember(activeTags, allowFilterActive) {
        FilterSettings.Tag.entries.filter { it in activeTags && (it != FilterSettings.Tag.ACTIVE || allowFilterActive) }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (ordered.isEmpty()) {
            Text(
                text = stringResource(CommonR.string.general_filter_empty_row_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
        } else {
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(ordered, key = { it.name }) { tag -> ActiveTagChip(tag = tag, onRemove = { onTagRemove(tag) }) }
            }
        }
        IconButton(onClick = onAddTags) {
            Icon(
                imageVector = Icons.TwoTone.Add,
                contentDescription = stringResource(CommonR.string.general_filter_add_action),
            )
        }
        BadgedBox(
            badge = {
                if (sortNonDefault) Badge()
            },
        ) {
            IconButton(onClick = onSort) {
                Icon(
                    imageVector = Icons.AutoMirrored.TwoTone.Sort,
                    contentDescription = stringResource(CommonR.string.general_sort_by_title),
                )
            }
        }
    }
}

@Composable
private fun ActiveTagChip(
    tag: FilterSettings.Tag,
    onRemove: () -> Unit,
) {
    val label = tagLabel(tag)
    val removeDesc = stringResource(CommonR.string.general_filter_remove_x_action, label)
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                imageVector = Icons.TwoTone.Close,
                contentDescription = removeDesc,
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
    )
}

@Composable
internal fun AppControlSortSheetContent(
    sort: SortSettings,
    allowSortSize: Boolean,
    allowSortScreenTime: Boolean,
    sizeSortCaveatVisible: Boolean,
    onSortModeChanged: (SortSettings.Mode) -> Unit,
    onSortDirectionToggle: () -> Unit,
    onSizeSortCaveatAck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        Text(
            text = stringResource(CommonR.string.general_sort_by_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            SortSettings.Mode.entries.forEach { entry ->
                val enabled = when (entry) {
                    SortSettings.Mode.SIZE -> allowSortSize
                    SortSettings.Mode.SCREEN_TIME -> allowSortScreenTime
                    else -> true
                }
                SortRow(
                    label = sortLabel(entry),
                    selected = sort.mode == entry,
                    enabled = enabled,
                    onClick = { onSortModeChanged(entry) },
                )
                if (entry == SortSettings.Mode.SIZE && sizeSortCaveatVisible && sort.mode == SortSettings.Mode.SIZE) {
                    SizeSortCaveat(onAck = onSizeSortCaveatAck)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            FilledTonalButton(onClick = onSortDirectionToggle) {
                Icon(
                    imageVector = if (sort.reversed) Icons.TwoTone.KeyboardArrowDown else Icons.TwoTone.KeyboardArrowUp,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(CommonR.string.general_sort_reverse_action))
            }
        }
    }
}

@Composable
internal fun AppControlTagsSheetContent(
    tags: Set<FilterSettings.Tag>,
    allowFilterActive: Boolean,
    onTagToggle: (FilterSettings.Tag) -> Unit,
    onTagsReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultTags = remember { FilterSettings().tags }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(CommonR.string.general_filter_by_tags_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onTagsReset,
                enabled = tags != defaultTags,
            ) {
                Text(stringResource(CommonR.string.general_reset_action))
            }
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TagChip(
                label = stringResource(R.string.appcontrol_tag_user),
                selected = FilterSettings.Tag.USER in tags,
                onClick = { onTagToggle(FilterSettings.Tag.USER) },
            )
            TagChip(
                label = stringResource(CommonR.string.general_tag_system),
                selected = FilterSettings.Tag.SYSTEM in tags,
                onClick = { onTagToggle(FilterSettings.Tag.SYSTEM) },
            )
            TagChip(
                label = stringResource(R.string.appcontrol_tag_enabled),
                selected = FilterSettings.Tag.ENABLED in tags,
                onClick = { onTagToggle(FilterSettings.Tag.ENABLED) },
            )
            TagChip(
                label = stringResource(R.string.appcontrol_tag_disabled),
                selected = FilterSettings.Tag.DISABLED in tags,
                onClick = { onTagToggle(FilterSettings.Tag.DISABLED) },
            )
            if (allowFilterActive) {
                TagChip(
                    label = stringResource(R.string.appcontrol_tag_active),
                    selected = FilterSettings.Tag.ACTIVE in tags,
                    onClick = { onTagToggle(FilterSettings.Tag.ACTIVE) },
                )
            }
            TagChip(
                label = stringResource(R.string.appcontrol_tag_not_installed),
                selected = FilterSettings.Tag.NOT_INSTALLED in tags,
                onClick = { onTagToggle(FilterSettings.Tag.NOT_INSTALLED) },
            )
        }
    }
}

@Composable
private fun SortRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

@Composable
private fun SizeSortCaveat(onAck: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.appcontrol_list_sortmode_size_caveat_msg),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            TextButton(
                onClick = onAck,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(CommonR.string.general_gotit_action))
            }
        }
    }
}

@Composable
private fun TagChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
internal fun tagLabel(tag: FilterSettings.Tag): String = when (tag) {
    FilterSettings.Tag.USER -> stringResource(R.string.appcontrol_tag_user)
    FilterSettings.Tag.SYSTEM -> stringResource(CommonR.string.general_tag_system)
    FilterSettings.Tag.ENABLED -> stringResource(R.string.appcontrol_tag_enabled)
    FilterSettings.Tag.DISABLED -> stringResource(R.string.appcontrol_tag_disabled)
    FilterSettings.Tag.ACTIVE -> stringResource(R.string.appcontrol_tag_active)
    FilterSettings.Tag.NOT_INSTALLED -> stringResource(R.string.appcontrol_tag_not_installed)
}

@Composable
private fun sortLabel(mode: SortSettings.Mode): String = when (mode) {
    SortSettings.Mode.NAME -> stringResource(R.string.appcontrol_list_sortmode_name_label)
    SortSettings.Mode.LAST_UPDATE -> stringResource(R.string.appcontrol_list_sortmode_updated_label)
    SortSettings.Mode.INSTALLED_AT -> stringResource(R.string.appcontrol_list_sortmode_installed_label)
    SortSettings.Mode.PACKAGENAME -> stringResource(R.string.appcontrol_list_sortmode_packagename_label)
    SortSettings.Mode.SIZE -> stringResource(R.string.appcontrol_list_sortmode_size_label)
    SortSettings.Mode.SCREEN_TIME -> stringResource(R.string.appcontrol_list_sortmode_screentime_label)
}
