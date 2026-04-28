package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.asComposable
import eu.darken.sdmse.common.compose.icons.icon
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.main.core.SDMTool

import eu.darken.sdmse.main.ui.dashboard.MainActionItem
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardActionIconSpacing
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardCard
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFilledActionButton
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardIconActionButton
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardOutlinedActionButton
import eu.darken.sdmse.main.ui.dashboard.cards.common.ProgressContainer

data class ToolDashboardCardItem(
    val toolType: SDMTool.Type,
    val isInitializing: Boolean,
    val result: SDMTool.Task.Result?,
    val progress: Progress.Data?,
    val showProRequirement: Boolean,
    val onScan: () -> Unit,
    val onDelete: (() -> Unit)?,
    val onViewTool: () -> Unit,
    val onViewDetails: () -> Unit,
    val onCancel: () -> Unit,
) : DashboardItem, MainActionItem {
    override val stableId: Long = toolType.hashCode().toLong()
}

@Composable
internal fun ToolDashboardCard(item: ToolDashboardCardItem) {
    val toolName = stringResource(toolNameRes(item.toolType))
    val toolDescription = stringResource(toolDescriptionRes(item.toolType))
    val clickable = item.progress == null && item.onDelete != null

    DashboardCard(
        onClick = item.onViewTool.takeIf { clickable },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = item.toolType.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (item.toolType == SDMTool.Type.CORPSEFINDER) Color.Unspecified else LocalContentColor.current,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = toolName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (item.isInitializing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 3.dp,
                )
            }
        }

        if (item.progress == null && item.result == null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = toolDescription,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (item.progress != null || item.result != null) {
            Spacer(modifier = Modifier.height(16.dp))
            ProgressContainer(
                modifier = Modifier.fillMaxWidth(),
                onClick = item.onViewTool.takeIf { item.result != null && item.progress == null },
                progress = item.progress,
                resultPrimary = item.result?.primaryInfo?.asComposable(),
                resultSecondary = item.result?.secondaryInfo?.asComposable()?.takeUnless { it.isBlank() },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.progress != null) {
                Spacer(modifier = Modifier.weight(1f))
                DashboardOutlinedActionButton(onClick = item.onCancel) {
                    Icon(
                        imageVector = Icons.Outlined.Cancel,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
                    Text(text = stringResource(CommonR.string.general_cancel_action))
                }
            } else {
                if (item.onDelete != null) {
                    DashboardIconActionButton(onClick = item.onViewDetails) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = stringResource(CommonR.string.general_show_details_action),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    DashboardFilledActionButton(
                        onClick = { item.onDelete.invoke() },
                        enabled = !item.isInitializing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(
                            imageVector = if (item.showProRequirement) Icons.Outlined.Stars else Icons.Outlined.Delete,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
                        Text(text = stringResource(CommonR.string.general_delete_action))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    DashboardFilledActionButton(
                        onClick = item.onScan,
                        enabled = !item.isInitializing,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = stringResource(CommonR.string.general_scan_action),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                    DashboardFilledActionButton(
                        onClick = item.onScan,
                        enabled = !item.isInitializing,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
                        Text(text = stringResource(CommonR.string.general_scan_action))
                    }
                }
            }
        }
    }
}

@Preview2
@Composable
private fun ToolDashboardCardPreview() {
    PreviewWrapper {
        ToolDashboardCard(
            item = ToolDashboardCardItem(
                toolType = SDMTool.Type.CORPSEFINDER,
                isInitializing = false,
                result = null,
                progress = Progress.Data(),
                showProRequirement = false,
                onScan = {},
                onDelete = {},
                onViewTool = {},
                onViewDetails = {},
                onCancel = {},
            ),
        )
    }
}
