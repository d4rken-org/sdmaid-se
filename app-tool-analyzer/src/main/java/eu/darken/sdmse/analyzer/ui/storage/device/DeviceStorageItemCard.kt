package eu.darken.sdmse.analyzer.ui.storage.device

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowRight
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.SdCard
import androidx.compose.material.icons.twotone.Usb
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.ui.storage.preview.previewDeviceStorage
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import java.time.Duration
import java.time.Instant
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import eu.darken.sdmse.common.stats.R as StatsR

@Composable
internal fun DeviceStorageItemCard(
    modifier: Modifier = Modifier,
    row: DeviceStorageViewModel.Row,
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

    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            // Header: hardware icon + label/identifier (icon centered between titles)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when (storage.hardware) {
                        DeviceStorage.Hardware.BUILT_IN -> Icons.TwoTone.Memory
                        DeviceStorage.Hardware.SDCARD -> Icons.TwoTone.SdCard
                        DeviceStorage.Hardware.USB -> Icons.TwoTone.Usb
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = storage.label.get(context),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // Hardware id (e.g. the volume UUID) when present; otherwise a generic label,
                    // since the built-in storage has no UUID to show.
                    val identifier = storage.id.internalId?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.analyzer_storage_identifier_internal)
                    Text(
                        text = identifier,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Description spans the full card width, not just the title column
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
            if (storage.setupIncomplete) {
                Text(
                    text = stringResource(R.string.analyzer_storage_content_type_app_setup_incomplete_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }

            Spacer(Modifier.size(12.dp))

            // Capacity: available + total text, with the bar directly beneath its own numbers.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val freeQuantity = ByteFormatter.stripSizeUnit(formattedFree)?.roundToInt() ?: 1
                val availableText = pluralStringResource(
                    R.plurals.analyzer_space_available,
                    freeQuantity,
                    formattedFree,
                )
                Text(
                    text = "$availableText · $percentUsed%",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (percentUsed > 95) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$formattedUsed / $formattedTotal",
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Spacer(Modifier.size(8.dp))

            LinearProgressIndicator(
                progress = { (percentUsed / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.size(16.dp))

            // Trend zone at the bottom: a divider, then the delta sentence + the explicit
            // "Storage Trend" button on one row. The button is the only path into the history
            // screen. Free users get a lock badge so the upgrade jump is expected, not a surprise.
            if (row.snapshots.size >= 2) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrendDeltaText(
                        modifier = Modifier.weight(1f),
                        snapshots = row.snapshots,
                    )
                    TextButton(onClick = onTrendClick) {
                        if (!row.isPro) {
                            Icon(
                                imageVector = Icons.TwoTone.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(4.dp))
                        }
                        Text(text = stringResource(StatsR.string.stats_storage_trend_action))
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendDeltaText(
    modifier: Modifier = Modifier,
    snapshots: List<SpaceSnapshotEntity>,
) {
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
        modifier = modifier,
    )
}

private fun previewSnapshots(): List<SpaceSnapshotEntity> {
    val capacity = 128L * 1024 * 1024 * 1024
    val base = Instant.parse("2026-05-25T12:00:00Z")
    val freeGb = listOf(48L, 47L, 45L, 46L, 44L, 43L, 42L)
    return freeGb.mapIndexed { index, gb ->
        SpaceSnapshotEntity(
            id = index.toLong(),
            storageId = "preview",
            recordedAt = base.plus(Duration.ofDays(index.toLong())),
            spaceFree = gb * 1024 * 1024 * 1024,
            spaceCapacity = capacity,
        )
    }
}

@Preview2
@Composable
private fun DeviceStorageItemCardProPreview() {
    PreviewWrapper {
        DeviceStorageItemCard(
            row = DeviceStorageViewModel.Row(
                storage = previewDeviceStorage(),
                snapshots = previewSnapshots(),
                isPro = true,
            ),
            onClick = {},
            onTrendClick = {},
        )
    }
}

@Preview2
@Composable
private fun DeviceStorageItemCardFreePreview() {
    PreviewWrapper {
        DeviceStorageItemCard(
            row = DeviceStorageViewModel.Row(
                storage = previewDeviceStorage(),
                snapshots = previewSnapshots(),
                isPro = false,
            ),
            onClick = {},
            onTrendClick = {},
        )
    }
}

@Preview2
@Composable
private fun DeviceStorageItemCardNoHistoryPreview() {
    PreviewWrapper {
        DeviceStorageItemCard(
            row = DeviceStorageViewModel.Row(
                storage = previewDeviceStorage(),
                snapshots = emptyList(),
                isPro = false,
            ),
            onClick = {},
            onTrendClick = {},
        )
    }
}
