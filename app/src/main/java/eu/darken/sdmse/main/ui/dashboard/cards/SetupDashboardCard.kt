package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.SettingsSuggest
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardActionIconSpacing
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardCard
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFilledActionButton
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFlatActionButton
import eu.darken.sdmse.setup.SetupManager
import eu.darken.sdmse.setup.SetupModule

data class SetupDashboardCardItem(
    val setupState: SetupManager.State,
    val onDismiss: () -> Unit,
    val onContinue: () -> Unit,
) : DashboardItem {
    override val stableId: Long = this.javaClass.hashCode().toLong()
}

@Composable
internal fun SetupDashboardCard(
    modifier: Modifier = Modifier,
    item: SetupDashboardCardItem,
) {
    val state = item.setupState
    val showContinue = !state.isHealerWorking || state.isIncomplete
    // D-pad entry into this card should land on the primary Continue action, not the Dismiss
    // button that happens to come first in the row. Only wired while Continue is composed —
    // an unattached requester would swallow the focus enter.
    val continueFocusRequester = remember { FocusRequester() }
    val enterOnContinue = if (showContinue) {
        Modifier
            .focusProperties { onEnter = { continueFocusRequester.requestFocus() } }
            .focusGroup()
    } else {
        Modifier
    }
    DashboardCard(
        modifier = modifier.then(enterOnContinue),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.sdm_not_happy),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (state.isIncomplete) R.string.setup_incomplete_card_title else R.string.setup_label,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (state.isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 3.dp,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(
                when {
                    state.isHealerWorking -> R.string.setup_incomplete_card_healing_in_progress_body
                    state.isLoading -> R.string.setup_checking_card_body
                    else -> R.string.setup_incomplete_card_body
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!state.isWorking) {
                DashboardFlatActionButton(onClick = item.onDismiss) {
                    Text(text = stringResource(CommonR.string.general_dismiss_action))
                }
            }
            if (showContinue) {
                DashboardFilledActionButton(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(continueFocusRequester),
                    onClick = item.onContinue,
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.SettingsSuggest,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
                    // Long translations must not wrap the label to a second line and grow the
                    // button taller than Dismiss: shrink the font to fit instead, with ellipsis
                    // as the backstop below the minimum size.
                    Text(
                        text = stringResource(
                            if (state.isIncomplete) {
                                R.string.setup_incomplete_card_continue_action
                            } else {
                                CommonR.string.general_view_action
                            },
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 10.sp,
                            maxFontSize = 14.sp,
                            stepSize = 0.25.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Preview2
@Composable
private fun SetupDashboardCardPreview() {
    val incompleteModule = object : SetupModule.State.Current {
        override val type: SetupModule.Type = SetupModule.Type.STORAGE
        override val isComplete: Boolean = false
    }

    PreviewWrapper {
        SetupDashboardCard(
            item = SetupDashboardCardItem(
                setupState = SetupManager.State(
                    moduleStates = listOf(incompleteModule),
                    isDismissed = false,
                    isHealerWorking = false,
                ),
                onDismiss = {},
                onContinue = {},
            ),
        )
    }
}
