package eu.darken.sdmse.corpsefinder.ui.list.items

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.SelectableListRow
import eu.darken.sdmse.common.compose.SelectableListRowIconBox
import eu.darken.sdmse.common.compose.icons.icon
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.corpsefinder.R as CorpseR
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.RiskLevel
import eu.darken.sdmse.corpsefinder.ui.icon
import eu.darken.sdmse.corpsefinder.ui.labelRes
import eu.darken.sdmse.corpsefinder.ui.list.CorpseFinderListViewModel
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpse
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpseRow

@Composable
fun CorpseRow(
    modifier: Modifier = Modifier,
    row: CorpseFinderListViewModel.Row,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onRiskChipClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val corpse = row.corpse

    val primary = corpse.lookup.userReadableName.get(context)
    val secondary = corpse.lookup.userReadablePath.get(context).removeSuffix(primary)
    val areaLabel = stringResource(corpse.filterType.labelRes)

    val itemsCount = corpse.content.size
    val sizeText = if (itemsCount > 0) {
        val quantity = pluralStringResource(CommonR.plurals.result_x_items, itemsCount, itemsCount)
        "$quantity, ${Formatter.formatShortFileSize(context, corpse.size)}"
    } else {
        Formatter.formatShortFileSize(context, corpse.size)
    }

    val riskBadge = when (corpse.riskLevel) {
        RiskLevel.NORMAL -> null
        RiskLevel.KEEPER -> CorpseR.string.corpsefinder_risk_keeper_chip to MaterialTheme.colorScheme.tertiary
        RiskLevel.COMMON -> CorpseR.string.corpsefinder_risk_common_chip to MaterialTheme.colorScheme.secondary
    }

    SelectableListRow(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        SelectableListRowIconBox(
            onClick = if (selectionActive) null else onDetailsClick,
            onLongClick = if (selectionActive) null else onLongClick,
        ) {
            Icon(
                imageVector = corpse.filterType.icon,
                contentDescription = stringResource(CommonR.string.general_details_label),
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primary,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondary.isNotEmpty()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.StartEllipsis,
                )
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = areaLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    riskBadge?.let { (labelRes, accent) ->
                        RiskPill(
                            label = stringResource(labelRes),
                            accent = accent,
                            onClick = onRiskChipClick,
                        )
                    }
                }
                Icon(
                    imageVector = corpse.lookup.fileType.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RiskPill(label: String, accent: Color, onClick: () -> Unit) {
    // Clickability lives on the inner Text instead of the Surface(onClick) overload — the latter
    // enforces a 48dp minimum interactive size that inflates the pill's layout bounds.
    Surface(
        color = accent.copy(alpha = 0.18f),
        contentColor = accent,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Preview2
@Composable
private fun CorpseRowPreview() {
    PreviewWrapper {
        CorpseRow(
            row = previewCorpseRow(),
            selected = false,
            selectionActive = false,
            onClick = {},
            onLongClick = {},
            onDetailsClick = {},
        )
    }
}

@Preview2
@Composable
private fun CorpseRowKeeperPreview() {
    PreviewWrapper {
        CorpseRow(
            row = previewCorpseRow(corpse = previewCorpse(riskLevel = RiskLevel.KEEPER)),
            selected = false,
            selectionActive = false,
            onClick = {},
            onLongClick = {},
            onDetailsClick = {},
        )
    }
}

@Preview2
@Composable
private fun CorpseRowCommonPreview() {
    PreviewWrapper {
        CorpseRow(
            row = previewCorpseRow(corpse = previewCorpse(riskLevel = RiskLevel.COMMON)),
            selected = false,
            selectionActive = false,
            onClick = {},
            onLongClick = {},
            onDetailsClick = {},
        )
    }
}

@Preview2
@Composable
private fun CorpseRowSelectedPreview() {
    PreviewWrapper {
        CorpseRow(
            row = previewCorpseRow(corpse = previewCorpse(riskLevel = RiskLevel.KEEPER)),
            selected = true,
            selectionActive = true,
            onClick = {},
            onLongClick = {},
            onDetailsClick = {},
        )
    }
}
