package eu.darken.sdmse.setup.shizuku

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.adb.shizuku.AdbBackend
import eu.darken.sdmse.common.adb.shizuku.ShizukuServiceState
import eu.darken.sdmse.common.coil.AppIconImage
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.Shizuku
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.container.toStub
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
    /** Brand name of the manager this build is allowed to send the user to. */
    @StringRes val installLabelRes: Int = R.string.setup_shizuku_install_manager_label,
    val onInstall: () -> Unit = {},
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
            // Offered whenever there is an app to open, connected or not: the card names the manager
            // SD Maid bound to, and the way to check on it is the same question in every state.
            val canOpen = item.state.isInstalled
            val restartRequired = item.state.restartRequiredFor != null
            // What the installed app calls itself, so a renamed fork isn't addressed as "Shizuku".
            val managerName = item.state.managerLabel ?: item.state.backend.label

            if (ready) {
                // Same success row as the Inventory/Notification/Storage cards, so "this worked"
                // looks identical everywhere in setup.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.setup_shizuku_service_ready_label, managerName),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else if (!failed) {
                // Single short line, so centring reads fine here and matches the other setup cards.
                Text(
                    text = when {
                        // A manager of the other family is installed: naming it beats repeating
                        // "nothing is installed", which no amount of refreshing would change.
                        restartRequired -> stringResource(
                            R.string.setup_shizuku_state_restart_required_label,
                            item.state.restartRequiredLabel ?: item.state.backend.other.label,
                        )

                        !item.state.isInstalled -> stringResource(R.string.setup_shizuku_state_not_installed_label)
                        else -> stringResource(R.string.setup_shizuku_state_waiting_label)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    textAlign = TextAlign.Center,
                )
            }

            // Nothing to open yet, so offer the way to get one. Not while restartRequired: there the
            // app IS installed and sending the user back to a store would be a dead end.
            if (!item.state.isInstalled && !restartRequired) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    OutlinedButton(onClick = item.onInstall) {
                        Icon(
                            imageVector = Icons.TwoTone.Download,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                        Text(
                            stringResource(
                                R.string.setup_shizuku_install_manager_action,
                                stringResource(item.installLabelRes),
                            )
                        )
                    }
                }
            }

            if (failed) {
                // SetupLimitationBox, same as the Automation and Inventory cards use for their own
                // "this won't work, here is what you can do" states. The explanation and its actions
                // read as one unit, and the text is start aligned: unlike the one-line states above,
                // this wraps to several lines, and centring those leaves both edges ragged.
                SetupLimitationBox(
                    title = stringResource(R.string.setup_shizuku_state_failed_title),
                    body = stringResource(R.string.setup_shizuku_service_failed_label, managerName),
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
                        ManagerButton(
                            pkg = item.state.pkg,
                            label = managerName,
                            onClick = item.onOpen,
                            modifier = Modifier.weight(1f),
                        )
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
                // over to the manager app, centred as it has always been.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    ManagerButton(
                        pkg = item.state.pkg,
                        label = managerName,
                        onClick = item.onOpen,
                    )
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

/**
 * Opens the manager SD Maid is bound to, shown as that app's own icon and name.
 *
 * The visible label is only the name, so the button reads the same as the app the user will land
 * in. "Open <name>" survives as the content description, which is what a screen reader needs and
 * the icon cannot convey.
 */
@Composable
private fun ManagerButton(
    pkg: Pkg.Id,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openDescription = stringResource(R.string.setup_shizuku_open_manager_action, label)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = openDescription },
    ) {
        AppIconImage(
            pkg = pkg.toStub(),
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Text(text = label)
    }
}

@Preview2
@Composable
private fun ShizukuSetupCardPreview() {
    PreviewWrapper {
        ShizukuSetupCard(
            item = ShizukuSetupCardItem(
                state = ShizukuSetupModule.Result(
                    pkg = "eu.darken.porter".toPkgId(),
                    useShizuku = true,
                    isCompatible = true,
                    isInstalled = true,
                    basicService = true,
                    serviceState = ShizukuServiceState.Available,
                    alsoHasRoot = false,
                    backend = AdbBackend.PORTER,
                    managerLabel = "Porter",
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
private fun ShizukuSetupCardReadyForkPreview() {
    PreviewWrapper {
        ShizukuSetupCard(
            item = ShizukuSetupCardItem(
                state = ShizukuSetupModule.Result(
                    pkg = "moe.shizuku.privileged.api".toPkgId(),
                    useShizuku = true,
                    isCompatible = true,
                    isInstalled = true,
                    basicService = true,
                    serviceState = ShizukuServiceState.Available,
                    alsoHasRoot = false,
                    backend = AdbBackend.SHIZUKU,
                    managerLabel = "Shizuku+",
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
private fun ShizukuSetupCardRestartRequiredPreview() {
    PreviewWrapper {
        ShizukuSetupCard(
            item = ShizukuSetupCardItem(
                state = ShizukuSetupModule.Result(
                    pkg = "eu.darken.porter".toPkgId(),
                    useShizuku = true,
                    isCompatible = true,
                    isInstalled = false,
                    basicService = false,
                    serviceState = ShizukuServiceState.NotChecked,
                    alsoHasRoot = false,
                    backend = AdbBackend.SHIZUKU,
                    restartRequiredFor = "eu.darken.porter".toPkgId(),
                    restartRequiredLabel = "Porter",
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
                    pkg = "eu.darken.porter".toPkgId(),
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
                    backend = AdbBackend.PORTER,
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
