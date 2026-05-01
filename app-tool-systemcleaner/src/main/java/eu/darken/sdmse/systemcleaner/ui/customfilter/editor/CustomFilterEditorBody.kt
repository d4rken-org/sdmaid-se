package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.compose.settings.dialogs.AgeInputDialog
import eu.darken.sdmse.common.compose.settings.dialogs.SizeInputDialog
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.ui.formatAge
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.common.sieve.SieveCriterium
import eu.darken.sdmse.systemcleaner.R as SystemCleanerR
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import java.time.Duration

private val AREA_TYPES = listOf(
    DataArea.Type.SDCARD,
    DataArea.Type.PUBLIC_DATA,
    DataArea.Type.PUBLIC_MEDIA,
    DataArea.Type.PUBLIC_OBB,
    DataArea.Type.PRIVATE_DATA,
    DataArea.Type.PORTABLE,
)

@Composable
internal fun CustomFilterEditorBody(
    config: CustomFilterConfig,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    onLabelChange: (String) -> Unit,
    onAddPath: (SegmentCriterium) -> Unit,
    onRemovePath: (SegmentCriterium) -> Unit,
    onSwapPath: (SieveCriterium, SieveCriterium) -> Unit,
    onAddName: (NameCriterium) -> Unit,
    onRemoveName: (NameCriterium) -> Unit,
    onSwapName: (SieveCriterium, SieveCriterium) -> Unit,
    onAddExclusion: (SegmentCriterium) -> Unit,
    onRemoveExclusion: (SegmentCriterium) -> Unit,
    onSwapExclusion: (SieveCriterium, SieveCriterium) -> Unit,
    onToggleArea: (DataArea.Type, Boolean) -> Unit,
    onToggleFileType: (FileType) -> Unit,
    onUpdateSizeMin: (Long?) -> Unit,
    onUpdateSizeMax: (Long?) -> Unit,
    onUpdateAgeMin: (Duration?) -> Unit,
    onUpdateAgeMax: (Duration?) -> Unit,
    onTaggedFieldFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var showSizeMin by remember { mutableStateOf(false) }
    var showSizeMax by remember { mutableStateOf(false) }
    var showAgeMin by remember { mutableStateOf(false) }
    var showAgeMax by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = contentBottomPadding),
    ) {
        OutlinedTextField(
            value = config.label,
            onValueChange = onLabelChange,
            label = { Text(stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_label_label)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )

        TaggedSection(
            taggedField = {
                TaggedInputField(
                    type = TagType.SEGMENTS,
                    hint = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_path_label),
                    tags = (config.pathCriteria ?: emptySet()).toList(),
                    onAdd = { onAddPath(it as SegmentCriterium) },
                    onRemove = { onRemovePath(it as SegmentCriterium) },
                    onModeChange = onSwapPath,
                    onFocusChange = { hasFocus -> if (hasFocus) onTaggedFieldFocused() },
                )
            },
            explanation = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_path_explanation),
        )

        TaggedSection(
            taggedField = {
                TaggedInputField(
                    type = TagType.NAME,
                    hint = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_name_label),
                    tags = (config.nameCriteria ?: emptySet()).toList(),
                    onAdd = { onAddName(it as NameCriterium) },
                    onRemove = { onRemoveName(it as NameCriterium) },
                    onModeChange = onSwapName,
                    onFocusChange = { hasFocus -> if (hasFocus) onTaggedFieldFocused() },
                )
            },
            explanation = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_name_explanation),
        )

        TaggedSection(
            taggedField = {
                TaggedInputField(
                    type = TagType.SEGMENTS,
                    hint = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_exclusions_label),
                    tags = (config.exclusionCriteria ?: emptySet()).toList(),
                    onAdd = { onAddExclusion(it as SegmentCriterium) },
                    onRemove = { onRemoveExclusion(it as SegmentCriterium) },
                    onModeChange = onSwapExclusion,
                    onFocusChange = { hasFocus -> if (hasFocus) onTaggedFieldFocused() },
                )
            },
            explanation = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_exclusions_explanation),
        )

        SectionCard {
            Text(
                text = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_areas_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AREA_TYPES.forEach { type ->
                    val selected = config.areas?.contains(type) == true
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleArea(type, !selected) },
                        label = { Text(type.raw) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_areas_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            Text(
                text = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_filetypes_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            CheckboxRow(
                label = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_filetypes_files_label),
                checked = config.fileTypes?.contains(FileType.FILE) == true,
                onClick = { onToggleFileType(FileType.FILE) },
            )
            CheckboxRow(
                label = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_filetypes_directories_label),
                checked = config.fileTypes?.contains(FileType.DIRECTORY) == true,
                onClick = { onToggleFileType(FileType.DIRECTORY) },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_filetypes_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            Text(
                text = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_size_label),
                style = MaterialTheme.typography.labelLarge,
            )
            ValueRow(
                label = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_size_minimum_label),
                value = config.sizeMinimum?.let { Formatter.formatShortFileSize(context, it) }
                    ?: stringResource(CommonR.string.general_na_label),
                onClick = { showSizeMin = true },
            )
            ValueRow(
                label = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_size_maximum_label),
                value = config.sizeMaximum?.let { Formatter.formatShortFileSize(context, it) }
                    ?: stringResource(CommonR.string.general_na_label),
                onClick = { showSizeMax = true },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_size_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            Text(
                text = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_age_label),
                style = MaterialTheme.typography.labelLarge,
            )
            ValueRow(
                label = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_age_minimum_label),
                value = config.ageMinimum?.let { formatAge(context, it) }
                    ?: stringResource(CommonR.string.general_na_label),
                onClick = { showAgeMin = true },
            )
            ValueRow(
                label = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_age_maximum_label),
                value = config.ageMaximum?.let { formatAge(context, it) }
                    ?: stringResource(CommonR.string.general_na_label),
                onClick = { showAgeMax = true },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_age_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_warning),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(32.dp),
        )
    }

    if (showSizeMin) {
        SizeInputDialog(
            titleRes = SystemCleanerR.string.systemcleaner_customfilter_editor_size_minimum_label,
            currentSize = config.sizeMinimum ?: 0L,
            onSave = { value ->
                onUpdateSizeMin(value)
                showSizeMin = false
            },
            onReset = {
                onUpdateSizeMin(null)
                showSizeMin = false
            },
            onDismiss = { showSizeMin = false },
        )
    }
    if (showSizeMax) {
        SizeInputDialog(
            titleRes = SystemCleanerR.string.systemcleaner_customfilter_editor_size_maximum_label,
            currentSize = config.sizeMaximum ?: 0L,
            onSave = { value ->
                onUpdateSizeMax(value)
                showSizeMax = false
            },
            onReset = {
                onUpdateSizeMax(null)
                showSizeMax = false
            },
            onDismiss = { showSizeMax = false },
        )
    }
    if (showAgeMin) {
        AgeInputDialog(
            titleRes = SystemCleanerR.string.systemcleaner_customfilter_editor_age_minimum_label,
            currentAge = config.ageMinimum ?: Duration.ZERO,
            onSave = { value ->
                onUpdateAgeMin(value)
                showAgeMin = false
            },
            onReset = {
                onUpdateAgeMin(null)
                showAgeMin = false
            },
            onDismiss = { showAgeMin = false },
        )
    }
    if (showAgeMax) {
        AgeInputDialog(
            titleRes = SystemCleanerR.string.systemcleaner_customfilter_editor_age_maximum_label,
            currentAge = config.ageMaximum ?: Duration.ZERO,
            onSave = { value ->
                onUpdateAgeMax(value)
                showAgeMax = false
            },
            onReset = {
                onUpdateAgeMax(null)
                showAgeMax = false
            },
            onDismiss = { showAgeMax = false },
        )
    }
}

@Composable
private fun TaggedSection(taggedField: @Composable () -> Unit, explanation: String) {
    SectionCard {
        taggedField()
        Spacer(Modifier.height(8.dp))
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onClick() })
        Text(label)
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
