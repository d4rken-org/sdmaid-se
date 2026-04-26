package eu.darken.sdmse.appcontrol.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.appcontrol.R
import eu.darken.sdmse.appcontrol.core.FilterSettings
import eu.darken.sdmse.appcontrol.core.SortSettings
import eu.darken.sdmse.common.R as CommonR
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppControlListFilters(
    modifier: Modifier = Modifier,
    options: AppControlListViewModel.DisplayOptions,
    allowSortSize: Boolean,
    allowSortScreenTime: Boolean,
    allowFilterActive: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onSortModeChanged: (SortSettings.Mode) -> Unit,
    onSortDirectionToggle: () -> Unit,
    onTagToggle: (FilterSettings.Tag) -> Unit,
) {
    var query by remember { mutableStateOf(options.searchQuery) }
    LaunchedEffect(options.searchQuery) {
        if (options.searchQuery != query) query = options.searchQuery
    }
    LaunchedEffect(query) {
        delay(300)
        if (query != options.searchQuery) onSearchQueryChanged(query)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(CommonR.string.general_search_action)) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchQueryChanged(query) }),
        )
        Spacer(Modifier.width(8.dp))
        SortControl(
            mode = options.listSort.mode,
            reversed = options.listSort.reversed,
            allowSortSize = allowSortSize,
            allowSortScreenTime = allowSortScreenTime,
            onSortModeChanged = onSortModeChanged,
            onSortDirectionToggle = onSortDirectionToggle,
        )
    }

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TagChip(
            label = stringResource(R.string.appcontrol_tag_user),
            selected = FilterSettings.Tag.USER in options.listFilter.tags,
            onClick = { onTagToggle(FilterSettings.Tag.USER) },
        )
        TagChip(
            label = stringResource(CommonR.string.general_tag_system),
            selected = FilterSettings.Tag.SYSTEM in options.listFilter.tags,
            onClick = { onTagToggle(FilterSettings.Tag.SYSTEM) },
        )
        TagChip(
            label = stringResource(R.string.appcontrol_tag_enabled),
            selected = FilterSettings.Tag.ENABLED in options.listFilter.tags,
            onClick = { onTagToggle(FilterSettings.Tag.ENABLED) },
        )
        TagChip(
            label = stringResource(R.string.appcontrol_tag_disabled),
            selected = FilterSettings.Tag.DISABLED in options.listFilter.tags,
            onClick = { onTagToggle(FilterSettings.Tag.DISABLED) },
        )
        if (allowFilterActive) {
            TagChip(
                label = stringResource(R.string.appcontrol_tag_active),
                selected = FilterSettings.Tag.ACTIVE in options.listFilter.tags,
                onClick = { onTagToggle(FilterSettings.Tag.ACTIVE) },
            )
        }
        TagChip(
            label = stringResource(R.string.appcontrol_tag_not_installed),
            selected = FilterSettings.Tag.NOT_INSTALLED in options.listFilter.tags,
            onClick = { onTagToggle(FilterSettings.Tag.NOT_INSTALLED) },
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortControl(
    mode: SortSettings.Mode,
    reversed: Boolean,
    allowSortSize: Boolean,
    allowSortScreenTime: Boolean,
    onSortModeChanged: (SortSettings.Mode) -> Unit,
    onSortDirectionToggle: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { menuOpen = true }) {
            Text(sortLabel(mode), style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        IconButton(onClick = onSortDirectionToggle) {
            Icon(
                imageVector = if (reversed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(CommonR.string.general_sort_reverse_action),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            SortSettings.Mode.entries.forEach { entry ->
                val enabled = when (entry) {
                    SortSettings.Mode.SIZE -> allowSortSize
                    SortSettings.Mode.SCREEN_TIME -> allowSortScreenTime
                    else -> true
                }
                DropdownMenuItem(
                    text = { Text(sortLabel(entry)) },
                    enabled = enabled,
                    onClick = {
                        menuOpen = false
                        onSortModeChanged(entry)
                    },
                )
            }
        }
    }
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
