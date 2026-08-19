package eu.darken.sdmse.setup.shizuku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
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
import eu.darken.sdmse.common.adb.shizuku.ShizukuServiceState
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.Shizuku
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.setup.SetupCardContainer
import eu.darken.sdmse.setup.SetupLimitationBox
import eu.darken.sdmse.setup.SetupCardItem
import eu.darken.sdmse.setup.root.RadioOption

data class ShizukuSetupCardItem(
    override val state: ShizukuSetupModule.Result,
    val onToggleUseShizuku: (Boolean?) -> Unit,
    val onOpen: () -> Unit,
    val onHelp: () -> Unit,
    val onRetry: () -> Unit = {},
    /**
     * Does this device match the hardware/ROM combination with the known upstream Shizuku problem?
     *
     * Resolved by the ViewModel, not here: the checks behind it hit the package manager, which must
     * not happen during recomposition.
     */
    val showKnownIssueHint: Boolean = false,
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

        if (item.state.useShizuku == true) {
            val ready = item.state.isInstalled && item.state.ourService
            // A settled "no", as opposed to "we haven't finished looking". Only this offers a retry:
            // showing one while a probe is still running is what made the card feel dead.
            val failed = item.state.isInstalled && item.state.serviceState.isTerminalFailure
            val canOpen = item.state.isInstalled && !item.state.isComplete

            if (!failed) {
                // Single short line, so centring reads fine here and matches the other setup cards.
                Text(
                    text = stringResource(
                        when {
                            !item.state.isInstalled -> R.string.setup_shizuku_state_not_installed_label
                            ready -> R.string.setup_shizuku_state_ready_label
                            else -> R.string.setup_shizuku_state_waiting_label
                        },
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

            if (failed) {
                // SetupLimitationBox, same as the Automation and Inventory cards use for their own
                // "this won't work, here is what you can do" states. The explanation and its actions
                // read as one unit, and the text is start aligned: unlike the one-line states above,
                // this wraps to several lines, and centring those leaves both edges ragged.
                SetupLimitationBox(
                    title = stringResource(R.string.setup_shizuku_state_failed_title),
                    body = stringResource(R.string.setup_shizuku_state_failed_label),
                    // No help button of its own: the card header already carries a help icon
                    // pointing at the same wiki page.
                    body2 = if (item.showKnownIssueHint) {
                        stringResource(R.string.setup_shizuku_state_known_issue_hint)
                    } else {
                        null
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = item.onOpen,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.setup_shizuku_card_title))
                        }
                        Button(
                            onClick = item.onRetry,
                            enabled = !item.state.isChecking,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(eu.darken.sdmse.common.R.string.general_retry_action))
                        }
                    }
                }
            } else if (canOpen) {
                // Not a failure, so there is nothing to explain and nothing to retry: just the way
                // over to Shizuku, centred as it has always been.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    OutlinedButton(onClick = item.onOpen) {
                        Text(stringResource(R.string.setup_shizuku_card_title))
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
                    serviceState = ShizukuServiceState.NotChecked,
                    alsoHasRoot = false,
                ),
                onToggleUseShizuku = {},
                onOpen = {},
                onHelp = {},
            ),
        )
    }
}

@Preview2
@Composable
private fun ShizukuSetupCardNotInstalledPreview() {
    PreviewWrapper {
        ShizukuSetupCard(
            item = ShizukuSetupCardItem(
                state = ShizukuSetupModule.Result(
                    pkg = "moe.shizuku.privileged.api".toPkgId(),
                    useShizuku = true,
                    isCompatible = true,
                    isInstalled = false,
                    basicService = false,
                    serviceState = ShizukuServiceState.NotChecked,
                    alsoHasRoot = false,
                ),
                onToggleUseShizuku = {},
                onOpen = {},
                onHelp = {},
            ),
        )
    }
}

@Preview2
@Composable
private fun ShizukuSetupCardFailedPreview() {
    PreviewWrapper {
        ShizukuSetupCard(
            item = ShizukuSetupCardItem(
                state = ShizukuSetupModule.Result(
                    pkg = "moe.shizuku.privileged.api".toPkgId(),
                    useShizuku = true,
                    isCompatible = true,
                    isInstalled = true,
                    basicService = true,
                    serviceState = ShizukuServiceState.TimedOut,
                    alsoHasRoot = false,
                ),
                onToggleUseShizuku = {},
                onOpen = {},
                onHelp = {},
                onRetry = {},
            ),
        )
    }
}

@Preview2
@Composable
private fun ShizukuSetupCardKnownIssuePreview() {
    PreviewWrapper {
        ShizukuSetupCard(
            item = ShizukuSetupCardItem(
                state = ShizukuSetupModule.Result(
                    pkg = "moe.shizuku.privileged.api".toPkgId(),
                    useShizuku = true,
                    isCompatible = true,
                    isInstalled = true,
                    basicService = true,
                    serviceState = ShizukuServiceState.Failed,
                    alsoHasRoot = false,
                ),
                onToggleUseShizuku = {},
                onOpen = {},
                onHelp = {},
                onRetry = {},
                showKnownIssueHint = true,
            ),
        )
    }
}

@Preview2
@Composable
private fun ShizukuSetupCardRetryingPreview() {
    PreviewWrapper {
        ShizukuSetupCard(
            item = ShizukuSetupCardItem(
                state = ShizukuSetupModule.Result(
                    pkg = "moe.shizuku.privileged.api".toPkgId(),
                    useShizuku = true,
                    isCompatible = true,
                    isInstalled = true,
                    basicService = true,
                    serviceState = ShizukuServiceState.TimedOut,
                    isChecking = true,
                    alsoHasRoot = false,
                ),
                onToggleUseShizuku = {},
                onOpen = {},
                onHelp = {},
                onRetry = {},
            ),
        )
    }
}
