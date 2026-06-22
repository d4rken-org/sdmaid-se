package eu.darken.sdmse.analyzer.ui.storage.app.items

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.ui.storage.preview.previewDeviceStorage
import eu.darken.sdmse.analyzer.ui.storage.preview.previewInstalled
import eu.darken.sdmse.analyzer.ui.storage.preview.previewPkgStat
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.pkgs.isArchived
import eu.darken.sdmse.common.R as CommonR

@Composable
internal fun AppDetailsHeaderCard(
    modifier: Modifier = Modifier,
    storage: DeviceStorage,
    pkgStat: AppCategory.PkgStat,
    onSettingsClick: () -> Unit = {},
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(pkgStat.pkg).build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f),
                )
                Spacer(Modifier.size(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = pkgStat.pkg.label?.get(context) ?: pkgStat.pkg.packageName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = pkgStat.id.pkgId.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val sizeText = Formatter.formatShortFileSize(context, pkgStat.totalSize)
            val occupiesText = stringResource(
                R.string.analyzer_app_details_app_occupies_x_on_y,
                sizeText,
                storage.label.get(context),
            )
            val displayText = if (pkgStat.pkg.isArchived) {
                "$occupiesText ${stringResource(R.string.analyzer_app_details_app_is_archived)}"
            } else {
                occupiesText
            }
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            Spacer(Modifier.size(12.dp))
            FilledTonalButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.TwoTone.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(CommonR.string.appcontrol_systemsettings_open_title))
            }
        }
    }
}

@Preview2
@Composable
private fun AppDetailsHeaderCardPreview() {
    PreviewWrapper {
        AppDetailsHeaderCard(
            storage = previewDeviceStorage(),
            pkgStat = previewPkgStat(
                pkg = previewInstalled(pkgName = "com.example.app", label = "Example App"),
            ),
            onSettingsClick = {},
        )
    }
}
