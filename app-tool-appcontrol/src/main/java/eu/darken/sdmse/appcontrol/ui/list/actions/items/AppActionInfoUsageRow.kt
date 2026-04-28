package eu.darken.sdmse.appcontrol.ui.list.actions.items

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.appcontrol.R
import eu.darken.sdmse.appcontrol.core.usage.UsageInfo
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.formatDuration
import eu.darken.sdmse.common.toSystemTimezone
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun AppActionInfoUsageRow(
    modifier: Modifier = Modifier,
    installedAt: Instant?,
    updatedAt: Instant?,
    usage: UsageInfo?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val dateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
    val sinceFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val naLabel = stringResource(CommonR.string.general_na_label)

    val installedText = stringResource(
        R.string.appcontrol_item_installedat_x_label,
        installedAt?.toSystemTimezone()?.format(dateFormatter) ?: naLabel,
    )
    val updatedText = stringResource(
        R.string.appcontrol_item_lastupdate_x_label,
        updatedAt?.toSystemTimezone()?.format(dateFormatter) ?: naLabel,
    )
    val screenTimeText = if (usage == null) {
        null
    } else {
        @Suppress("DEPRECATION")
        val durationTxt = usage.screenTime.formatDuration(abbrev = DateUtils.LENGTH_LONG)
        val sinceTxt = usage.screenTimeSince.toSystemTimezone().format(sinceFormatter)
        stringResource(R.string.appcontrol_item_screentime_x_since_y_label, durationTxt, sinceTxt)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.TwoTone.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = installedText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = updatedText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (screenTimeText != null) {
                Text(
                    text = screenTimeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
