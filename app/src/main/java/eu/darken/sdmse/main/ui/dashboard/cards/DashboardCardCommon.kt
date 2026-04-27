package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.appcleaner.R as AppCleanerR
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.compose.SdmMascot
import eu.darken.sdmse.common.compose.SdmMascotMode
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.asComposable
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.corpsefinder.R as CorpseFinderR
import eu.darken.sdmse.deduplicator.R as DeduplicatorR
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.systemcleaner.R as SystemCleanerR

internal val DashboardActionIconSpacing = 4.dp
private val DashboardActionButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
private val DashboardIconActionButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

@Composable
internal fun DashboardCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .then(clickableModifier),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = androidx.compose.material3.contentColorFor(containerColor),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
internal fun DashboardFlatActionButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        contentPadding = DashboardActionButtonPadding,
        content = content,
    )
}

@Composable
internal fun DashboardIconActionButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        contentPadding = DashboardIconActionButtonPadding,
        content = content,
    )
}

@Composable
internal fun DashboardFilledActionButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        contentPadding = DashboardActionButtonPadding,
        content = content,
    )
}

@Composable
internal fun DashboardFilledTonalActionButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        contentPadding = DashboardActionButtonPadding,
        content = content,
    )
}

@Composable
internal fun DashboardOutlinedActionButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        contentPadding = DashboardActionButtonPadding,
        content = content,
    )
}

@Composable
internal fun SimpleToolCardHeader(
    iconRes: Int,
    title: String,
    subtitle: String,
    isInitializing: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (isInitializing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 3.dp,
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
internal fun ProgressContainer(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
    progress: Progress.Data?,
    resultPrimary: String?,
    resultSecondary: String?,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            when {
                progress != null -> DashboardProgress(progress)
                else -> {
                    resultPrimary?.takeUnless { it.isBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    resultSecondary?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DashboardProgress(progress: Progress.Data) {
    val context = LocalContext.current
    progress.icon?.let {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = it.asComposable(),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = progress.primary.asComposable(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } ?: Text(
        text = progress.primary.asComposable(),
        style = MaterialTheme.typography.bodyMedium,
    )

    val secondary = progress.secondary.asComposable()
    if (secondary.isNotEmpty()) {
        Text(
            text = secondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (progress.count !is Progress.Count.None) {
        Spacer(modifier = Modifier.height(8.dp))
        when (val count = progress.count) {
            is Progress.Count.Indeterminate -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is Progress.Count.Percent -> LinearProgressIndicator(
                progress = { if (count.max > 0) count.current.toFloat() / count.max.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth(),
            )

            is Progress.Count.Counter -> LinearProgressIndicator(
                progress = { if (count.max > 0) count.current.toFloat() / count.max.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth(),
            )

            is Progress.Count.Size -> LinearProgressIndicator(
                progress = { if (count.max > 0) count.current.toFloat() / count.max.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth(),
            )

            is Progress.Count.None -> Unit
        }

        val countText = progress.count.displayValue(context).orEmpty()
        if (countText.isNotEmpty() && progress.count !is Progress.Count.Indeterminate) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = countText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}


@Composable
internal fun DebugToggleRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    highlight: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (highlight) MaterialTheme.colorScheme.error else LocalContentColor.current,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
internal fun NewBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = stringResource(CommonR.string.general_new_badge_label),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
internal fun Mascot(
    modifier: Modifier = Modifier,
    mascotMode: SdmMascotMode = SdmMascotMode.Animated,
) {
    SdmMascot(
        modifier = modifier,
        mode = mascotMode,
    )
}

internal fun toolIconRes(type: SDMTool.Type): Int = when (type) {
    SDMTool.Type.CORPSEFINDER -> CommonR.drawable.ghost
    SDMTool.Type.SYSTEMCLEANER -> CommonR.drawable.ic_baseline_view_list_24
    SDMTool.Type.APPCLEANER -> CommonR.drawable.ic_recycle
    SDMTool.Type.DEDUPLICATOR -> CommonR.drawable.ic_content_duplicate_24
    else -> error("Unsupported tool type: $type")
}

internal fun toolNameRes(type: SDMTool.Type): Int = when (type) {
    SDMTool.Type.CORPSEFINDER -> CommonR.string.corpsefinder_tool_name
    SDMTool.Type.SYSTEMCLEANER -> CommonR.string.systemcleaner_tool_name
    SDMTool.Type.APPCLEANER -> CommonR.string.appcleaner_tool_name
    SDMTool.Type.DEDUPLICATOR -> CommonR.string.deduplicator_tool_name
    else -> error("Unsupported tool type: $type")
}

internal fun toolDescriptionRes(type: SDMTool.Type): Int = when (type) {
    SDMTool.Type.CORPSEFINDER -> CorpseFinderR.string.corpsefinder_explanation_short
    SDMTool.Type.SYSTEMCLEANER -> SystemCleanerR.string.systemcleaner_explanation_short
    SDMTool.Type.APPCLEANER -> AppCleanerR.string.appcleaner_explanation_short
    SDMTool.Type.DEDUPLICATOR -> DeduplicatorR.string.deduplicator_explanation_short
    else -> error("Unsupported tool type: $type")
}
