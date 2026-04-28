package eu.darken.sdmse.analyzer.ui.storage.device

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.SdCard
import androidx.compose.material.icons.twotone.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import eu.darken.sdmse.stats.ui.spacehistory.SpaceHistoryChartView
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
internal fun DeviceStorageItemCard(
    row: DeviceStorageViewModel.Row,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onTrendClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val storage = row.storage

    val percentUsed: Int = if (storage.spaceCapacity > 0L) {
        ((storage.spaceUsed.toDouble() / storage.spaceCapacity.toDouble()) * 100).toInt()
    } else 0

    val formattedUsed = Formatter.formatShortFileSize(context, storage.spaceUsed)
    val formattedTotal = Formatter.formatShortFileSize(context, storage.spaceCapacity)
    val formattedFree = Formatter.formatShortFileSize(context, storage.spaceFree)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: hardware icon + label/identifier/description
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = when (storage.hardware) {
                        DeviceStorage.Hardware.BUILT_IN -> Icons.TwoTone.Memory
                        DeviceStorage.Hardware.SDCARD -> Icons.TwoTone.SdCard
                        DeviceStorage.Hardware.USB -> Icons.TwoTone.Usb
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(top = 2.dp),
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = storage.label.get(context),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    storage.id.internalId?.takeIf { it.isNotBlank() }?.let { id ->
                        Text(
                            text = id,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(
                            when (storage.type) {
                                DeviceStorage.Type.PRIMARY -> R.string.analyzer_storage_type_primary_description
                                DeviceStorage.Type.SECONDARY -> R.string.analyzer_storage_type_secondary_description
                                DeviceStorage.Type.PORTABLE -> R.string.analyzer_storage_type_tertiary_description
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (storage.setupIncomplete) {
                        Text(
                            text = stringResource(R.string.analyzer_storage_content_type_app_setup_incomplete_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.size(12.dp))

            // Capacity bar with percent
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { (percentUsed / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = "$percentUsed%",
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            // Trend chart (compact) + delta
            if (row.snapshots.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onTrendClick),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            SpaceHistoryChartView(ctx).apply {
                                isCompact = true
                            }
                        },
                        update = { it.setData(row.snapshots) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                    )
                    if (row.snapshots.size >= 2) {
                        TrendDeltaText(snapshots = row.snapshots)
                    }
                }
            }

            Spacer(Modifier.size(12.dp))

            // Available + capacity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val freeQuantity = ByteFormatter.stripSizeUnit(formattedFree)?.roundToInt() ?: 1
                Text(
                    text = pluralStringResource(
                        R.plurals.analyzer_space_available,
                        freeQuantity,
                        formattedFree,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (percentUsed > 95) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$formattedUsed / $formattedTotal",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun TrendDeltaText(snapshots: List<SpaceSnapshotEntity>) {
    val context = LocalContext.current
    val oldest = snapshots.first().let { it.spaceCapacity - it.spaceFree }
    val newest = snapshots.last().let { it.spaceCapacity - it.spaceFree }
    val delta = newest - oldest
    val absDelta = Formatter.formatShortFileSize(context, delta.absoluteValue)
    val signed = when {
        delta > 0 -> "+$absDelta"
        delta < 0 -> "-$absDelta"
        else -> absDelta
    }
    val color = when {
        delta > 0 -> MaterialTheme.colorScheme.error
        delta < 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = stringResource(R.string.analyzer_storage_trend_delta_in_7d, signed),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(top = 4.dp),
    )
}
