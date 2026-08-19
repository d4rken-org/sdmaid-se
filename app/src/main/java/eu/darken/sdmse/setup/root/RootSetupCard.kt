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
import androidx.compose.material3.Button
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
import eu.darken.sdmse.setup.SetupLimitationBox
import eu.darken.sdmse.common.R as CommonR

data class RootSetupCardItem(
    override val state: RootSetupModule.Result,
    val onToggleUseRoot: (Boolean?) -> Unit,
    val onHelp: () -> Unit,
    val onRetry: () -> Unit,
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
            // SD Maid asked for root and didn't get it, which is worth explaining and retrying.
            // Whether a known root manager is installed only picks the wording: hidden or built-in
            // root shows up as no manager at all.
            // Result.isComplete deliberately stays as it is: treating "opted into root that does not
            // work" as incomplete would strand every user who picks "Try to use root access" on an
            // ordinary unrooted phone, so this box can render on a module that reports itself complete.
            val failed = item.state.useRoot == true && !item.state.ourService

            if (!failed) {
                // Reaching this branch means ourService is true, so root is ready. The probe has
                // settled by the time we render a Result (Loading shows a spinner card instead),
                // so there is no third "still waiting" state to report here.
                Text(
                    text = stringResource(R.string.setup_root_state_ready_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                // Start aligned, multi-line explanation instead of the centred one-liner, matching how
                // the Shizuku card presents its settled failure. No help button of its own: the card
                // header already carries a help icon pointing at the same wiki page.
                SetupLimitationBox(
                    title = stringResource(R.string.setup_root_state_failed_title),
                    body = stringResource(
                        if (item.state.isInstalled) R.string.setup_root_state_failed_body
                        else R.string.setup_root_state_failed_body_nomanager,
                    ),
                ) {
                    // No enabled guard: a refresh drives accessState to Checking, the module maps
                    // that to Loading, and the whole card is replaced by a loading card while the
                    // probe runs.
                    Button(
                        onClick = item.onRetry,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(CommonR.string.general_retry_action))
                    }
                }
            }
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
                onRetry = {},
            ),
        )
    }
}

@Preview2
@Composable
private fun RootSetupCardFailedPreview() {
    PreviewWrapper {
        RootSetupCard(
            item = RootSetupCardItem(
                state = RootSetupModule.Result(
                    useRoot = true,
                    isInstalled = true,
                    ourService = false,
                ),
                onToggleUseRoot = {},
                onHelp = {},
                onRetry = {},
            ),
        )
    }
}

@Preview2
@Composable
private fun RootSetupCardFailedNoManagerPreview() {
    PreviewWrapper {
        RootSetupCard(
            item = RootSetupCardItem(
                state = RootSetupModule.Result(
                    useRoot = true,
                    isInstalled = false,
                    ourService = false,
                ),
                onToggleUseRoot = {},
                onHelp = {},
                onRetry = {},
            ),
        )
    }
}
