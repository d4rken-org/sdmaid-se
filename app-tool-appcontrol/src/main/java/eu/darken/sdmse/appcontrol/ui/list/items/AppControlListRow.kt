package eu.darken.sdmse.appcontrol.ui.list.items

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import eu.darken.sdmse.appcontrol.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.SortSettings
import eu.darken.sdmse.appcontrol.ui.list.AppControlListViewModel
import eu.darken.sdmse.appcontrol.ui.list.AppInfoTagsRow
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.formatDuration
import eu.darken.sdmse.common.toSystemTimezone
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun AppControlListRow(
    modifier: Modifier = Modifier,
    row: AppControlListViewModel.Row,
    sortMode: SortSettings.Mode,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val appInfo = row.appInfo
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(appInfo.pkg).build(),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appInfo.label.get(context),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = appInfo.pkg.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val userLabel = appInfo.userProfile?.getHumanLabel()?.get(context)
            if (userLabel != null) {
                Text(
                    text = userLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val secondary = secondaryInfoFor(appInfo, sortMode, context)
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AppInfoTagsRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                appInfo = appInfo,
            )
        }
        if (appInfo.sizes != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = Formatter.formatShortFileSize(context, appInfo.sizes!!.total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun secondaryInfoFor(
    appInfo: AppInfo,
    sortMode: SortSettings.Mode,
    context: android.content.Context,
): String? {
    val installFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    val sinceFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    val naLabel = stringResource(CommonR.string.general_na_label)

    return when (sortMode) {
        SortSettings.Mode.INSTALLED_AT -> stringResource(
            R.string.appcontrol_item_installedat_x_label,
            appInfo.installedAt?.toSystemTimezone()?.format(installFormatter) ?: naLabel,
        )

        SortSettings.Mode.LAST_UPDATE -> stringResource(
            R.string.appcontrol_item_lastupdate_x_label,
            appInfo.updatedAt?.toSystemTimezone()?.format(installFormatter) ?: naLabel,
        )

        SortSettings.Mode.SCREEN_TIME -> {
            val usage = appInfo.usage
            if (usage == null) {
                naLabel
            } else {
                val since = usage.screenTimeSince.toSystemTimezone().format(sinceFormatter)
                val durationTxt = usage.screenTime.formatDuration()
                stringResource(R.string.appcontrol_item_screentime_x_since_y_label, durationTxt, since)
            }
        }

        SortSettings.Mode.NAME, SortSettings.Mode.PACKAGENAME, SortSettings.Mode.SIZE ->
            "${appInfo.pkg.versionName ?: "?"} (${appInfo.pkg.versionCode})"
    }
}
