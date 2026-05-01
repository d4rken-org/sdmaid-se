package eu.darken.sdmse.setup.root

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AdminPanelSettings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.setup.SetupCardContainer
import eu.darken.sdmse.setup.SetupCardItem

data class RootSetupCardItem(
    override val state: RootSetupModule.Result,
    val onToggleUseRoot: (Boolean?) -> Unit,
    val onHelp: () -> Unit,
) : SetupCardItem

@Composable
internal fun RootSetupCard(
    item: RootSetupCardItem,
    modifier: Modifier = Modifier,
) {
    SetupCardContainer(
        icon = Icons.TwoTone.AdminPanelSettings,
        title = stringResource(R.string.setup_root_card_title),
        modifier = modifier,
        onHelp = item.onHelp,
    ) {
        Text(
            text = stringResource(R.string.setup_root_card_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        if (item.state.useRoot == true) {
            val ready = item.state.ourService
            val baseText = stringResource(
                if (ready) R.string.setup_root_state_ready_label
                else R.string.setup_root_state_waiting_label,
            )
            val stateText = if (!item.state.isInstalled) "$baseText ?" else baseText
            Text(
                text = stateText,
                style = MaterialTheme.typography.labelMedium,
                color = if (ready) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .selectableGroup(),
        ) {
            RadioOption(
                label = stringResource(R.string.setup_root_enable_root_use_label),
                selected = item.state.useRoot == true,
                onSelect = { item.onToggleUseRoot(true) },
            )
            RadioOption(
                label = stringResource(R.string.setup_root_disable_root_use_label),
                selected = item.state.useRoot == false,
                onSelect = { item.onToggleUseRoot(false) },
            )
        }
        Text(
            text = stringResource(R.string.setup_root_card_body2),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
internal fun RadioOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview2
@Composable
private fun RootSetupCardPreview() {
    PreviewWrapper {
        RootSetupCard(
            item = RootSetupCardItem(
                state = RootSetupModule.Result(
                    useRoot = true,
                    isInstalled = true,
                    ourService = true,
                ),
                onToggleUseRoot = {},
                onHelp = {},
            ),
        )
    }
}
