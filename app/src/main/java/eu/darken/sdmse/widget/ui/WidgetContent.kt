package eu.darken.sdmse.widget.ui

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import eu.darken.sdmse.R
import eu.darken.sdmse.main.core.shortcuts.AppShortcut
import eu.darken.sdmse.main.ui.MainActivity
import eu.darken.sdmse.widget.WidgetRenderState
import eu.darken.sdmse.common.ui.R as CommonUiR

@Composable
internal fun WidgetContent(state: WidgetRenderState) {
    val context = LocalContext.current
    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.background)
                .cornerRadius(20.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                .padding(12.dp),
            // Centre the content so a short widget in a taller cell has balanced margins
            // instead of blank space at the bottom.
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is WidgetRenderState.Data -> StorageContent(state)
                WidgetRenderState.Unavailable -> UnavailableContent()
            }
        }
    }
}

@Composable
private fun StorageContent(data: WidgetRenderState.Data) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        BrandingHeader(data.freedBytes)
        Spacer(GlanceModifier.height(8.dp))
        data.storages.forEachIndexed { index, entry ->
            if (index > 0) Spacer(GlanceModifier.height(6.dp))
            StorageRow(entry)
        }
        Spacer(GlanceModifier.height(8.dp))
        CleanButton(GlanceModifier.fillMaxWidth())
    }
}

@Composable
private fun BrandingHeader(freedBytes: Long) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(R.mipmap.ic_launcher_round),
            contentDescription = null,
            modifier = GlanceModifier.size(24.dp),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = context.getString(R.string.widget_home_title),
            style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = context.getString(R.string.widget_home_freed_label, formatSize(context, freedBytes)),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun StorageRow(entry: WidgetRenderState.Data.StorageEntry) {
    val context = LocalContext.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = context.getString(storageLabelRes(entry.kind)),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = "${formatSize(context, entry.usedBytes)} / ${formatSize(context, entry.totalBytes)}",
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(3.dp))
        LinearProgressIndicator(
            progress = entry.usedRatio,
            modifier = GlanceModifier.fillMaxWidth().height(7.dp).cornerRadius(4.dp),
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.secondaryContainer,
        )
    }
}

@Composable
private fun CleanButton(modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .background(GlanceTheme.colors.primary)
            .cornerRadius(22.dp)
            .clickable(actionStartActivity(AppShortcut.MainAction.OneTap.createIntent(context)))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(CommonUiR.drawable.ic_baseline_delete_sweep_24),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
            modifier = GlanceModifier.size(18.dp),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = context.getString(R.string.widget_home_clean_action),
            style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

@Composable
private fun UnavailableContent() {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(R.mipmap.ic_launcher_round),
            contentDescription = null,
            modifier = GlanceModifier.size(28.dp),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = context.getString(R.string.widget_home_unavailable),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
    }
}

private fun storageLabelRes(kind: WidgetRenderState.Data.StorageEntry.Kind): Int = when (kind) {
    WidgetRenderState.Data.StorageEntry.Kind.INTERNAL -> R.string.widget_home_storage_internal
    WidgetRenderState.Data.StorageEntry.Kind.EXTERNAL -> R.string.widget_home_storage_external
}

private fun formatSize(context: Context, bytes: Long): String =
    Formatter.formatShortFileSize(context, bytes)
