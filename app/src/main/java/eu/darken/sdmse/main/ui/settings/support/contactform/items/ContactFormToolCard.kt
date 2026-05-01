package eu.darken.sdmse.main.ui.settings.support.contactform.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AppSettingsAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.ui.settings.support.contactform.SupportContactFormViewModel.Tool

@Composable
fun ContactFormToolCard(
    selected: Tool,
    onChange: (Tool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.AppSettingsAlt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.support_contact_tool_label),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
            ) {
                for (option in Tool.entries) {
                    FilterChip(
                        selected = option == selected,
                        onClick = { onChange(option) },
                        label = { Text(stringResource(option.labelRes)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
    }
}

private val Tool.labelRes: Int
    get() = when (this) {
        Tool.APP_CLEANER -> R.string.support_contact_tool_appcleaner_label
        Tool.CORPSE_FINDER -> R.string.support_contact_tool_corpsefinder_label
        Tool.SYSTEM_CLEANER -> R.string.support_contact_tool_systemcleaner_label
        Tool.DEDUPLICATOR -> R.string.support_contact_tool_deduplicator_label
        Tool.ANALYZER -> R.string.support_contact_tool_analyzer_label
        Tool.APP_CONTROL -> R.string.support_contact_tool_appcontrol_label
        Tool.SCHEDULER -> R.string.support_contact_tool_scheduler_label
        Tool.GENERAL -> R.string.support_contact_tool_general_label
    }

@Preview2
@Composable
private fun ContactFormToolCardPreview() {
    PreviewWrapper {
        ContactFormToolCard(selected = Tool.APP_CLEANER, onChange = {})
    }
}
