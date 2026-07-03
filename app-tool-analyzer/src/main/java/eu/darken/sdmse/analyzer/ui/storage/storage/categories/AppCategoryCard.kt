package eu.darken.sdmse.analyzer.ui.storage.storage.categories

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Apps
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
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.ui.storage.preview.previewDeviceStorage
import eu.darken.sdmse.analyzer.ui.storage.preview.previewPkgStat
import eu.darken.sdmse.analyzer.ui.storage.storage.StorageContentViewModel
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import kotlin.math.roundToInt

@Composable
internal fun AppCategoryCard(
    modifier: Modifier = Modifier,
    row: StorageContentViewModel.Row.Apps,
    onClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val storage = row.storage
    val content = row.category
    val percentUsed: Int = if (storage.spaceUsed > 0L) {
        ((content.spaceUsed.toDouble() / storage.spaceUsed.toDouble()) * 100).toInt()
    } else 0
    val usedText = Formatter.formatShortFileSize(context, content.spaceUsed)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.analyzer_storage_content_type_app_label),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(R.string.analyzer_storage_content_type_app_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LinearProgressIndicator(
                    progress = { (percentUsed / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f),
                )
                if (content.setupIncomplete) {
                    Text(
                        text = stringResource(R.string.analyzer_storage_content_type_app_setup_incomplete_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    val quantity = ByteFormatter.stripSizeUnit(usedText)?.roundToInt() ?: 1
                    Text(
                        text = pluralStringResource(R.plurals.analyzer_space_used, quantity, usedText),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Preview2
@Composable
private fun AppCategoryCardPreview() {
    val storage = previewDeviceStorage()
    val pkgStat = previewPkgStat()
    PreviewWrapper {
        AppCategoryCard(
            row = StorageContentViewModel.Row.Apps(
                storage = storage,
                category = AppCategory(
                    storageId = storage.id,
                    pkgStats = mapOf(pkgStat.id to pkgStat),
                ),
            ),
            onClick = {},
        )
    }
}
