package eu.darken.sdmse.setup.shizuku

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.Shizuku
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.setup.SetupCardContainer
import eu.darken.sdmse.setup.SetupCardItem
import eu.darken.sdmse.setup.root.RadioOption

data class ShizukuSetupCardItem(
    override val state: ShizukuSetupModule.Result,
    val onToggleUseShizuku: (Boolean?) -> Unit,
    val onOpen: () -> Unit,
    val onHelp: () -> Unit,
) : SetupCardItem

@Composable
internal fun ShizukuSetupCard(
    item: ShizukuSetupCardItem,
    modifier: Modifier = Modifier,
) {
    SetupCardContainer(
        icon = SdmIcons.Shizuku,
        title = stringResource(R.string.setup_shizuku_card_title),
        modifier = modifier,
        onHelp = item.onHelp,
    ) {
        val bodyText = buildString {
            append(stringResource(R.string.setup_shizuku_card_body))
            if (item.state.alsoHasRoot) {
                append("\n")
                append(stringResource(R.string.setup_shizuku_card_root_info))
            }
        }
        Text(
            text = bodyText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        if (item.state.useShizuku == true && item.state.isInstalled) {
            val ready = item.state.ourService
            Text(
                text = stringResource(
                    if (ready) R.string.setup_shizuku_state_ready_label
                    else R.string.setup_shizuku_state_waiting_label,
                ),
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

        if (item.state.isInstalled && item.state.useShizuku == true && !item.state.isComplete) {
            OutlinedButton(
                onClick = item.onOpen,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.setup_shizuku_card_title))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .selectableGroup(),
        ) {
            RadioOption(
                label = stringResource(R.string.setup_shizuku_enable_shizuku_use_label),
                selected = item.state.useShizuku == true,
                onSelect = { item.onToggleUseShizuku(true) },
            )
            RadioOption(
                label = stringResource(R.string.setup_shizuku_disable_shizuku_use_label),
                selected = item.state.useShizuku == false,
                onSelect = { item.onToggleUseShizuku(false) },
            )
        }

        Text(
            text = stringResource(R.string.setup_shizuku_card_body2),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
    }
}

@Preview2
@Composable
private fun ShizukuSetupCardPreview() {
    PreviewWrapper {
        ShizukuSetupCard(
            item = ShizukuSetupCardItem(
                state = ShizukuSetupModule.Result(
                    pkg = "moe.shizuku.privileged.api".toPkgId(),
                    useShizuku = true,
                    isCompatible = true,
                    isInstalled = true,
                    basicService = true,
                    ourService = false,
                    alsoHasRoot = false,
                ),
                onToggleUseShizuku = {},
                onOpen = {},
                onHelp = {},
            ),
        )
    }
}
