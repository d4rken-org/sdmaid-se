package eu.darken.sdmse.analyzer.ui.storage.app.items

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowRight
import androidx.compose.material.icons.twotone.Inventory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.ui.storage.preview.previewContentGroup
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

@Composable
internal fun AppDetailsGroupRow(
    modifier: Modifier = Modifier,
    group: ContentGroup,
    @StringRes labelRes: Int,
    @StringRes descRes: Int,
    icon: ImageVector,
    onClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val sizeText = Formatter.formatShortFileSize(context, group.groupSize)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Text(
                    text = stringResource(descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
            Icon(Icons.AutoMirrored.TwoTone.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Preview2
@Composable
private fun AppDetailsGroupRowPreview() {
    PreviewWrapper {
        AppDetailsGroupRow(
            group = previewContentGroup(label = "App code"),
            labelRes = R.string.analyzer_storage_content_app_code_label,
            descRes = R.string.analyzer_storage_content_app_code_description,
            icon = Icons.TwoTone.Inventory,
            onClick = {},
        )
    }
}
