package eu.darken.sdmse.appcontrol.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.darken.sdmse.appcontrol.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.export.AppExportType
import eu.darken.sdmse.common.pkgs.isArchived
import eu.darken.sdmse.common.pkgs.isDebuggable
import eu.darken.sdmse.common.pkgs.isEnabled
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.pkgs.isUninstalled

@Composable
fun AppInfoTagsRow(
    modifier: Modifier = Modifier,
    appInfo: AppInfo,
) {
    val active = appInfo.isActive == true
    val system = appInfo.pkg.isSystemApp
    val debug = appInfo.pkg.isDebuggable
    val archived = appInfo.pkg.isArchived
    val uninstalled = appInfo.pkg.isUninstalled
    val disabled = !appInfo.pkg.isEnabled
    val apkBase = appInfo.exportType == AppExportType.APK
    val apkBundle = appInfo.exportType == AppExportType.BUNDLE

    val anyVisible = active || system || debug || archived || uninstalled || disabled || apkBase || apkBundle
    if (!anyVisible) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (active) {
            Tag(
                text = stringResource(R.string.appcontrol_tag_active),
                background = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
        if (disabled) {
            Tag(
                text = stringResource(R.string.appcontrol_tag_disabled),
                background = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        if (archived) {
            Tag(
                text = stringResource(R.string.appcontrol_tag_archived),
                background = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        if (uninstalled) {
            Tag(
                text = stringResource(R.string.appcontrol_tag_uninstalled),
                background = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        if (system) {
            Tag(
                text = stringResource(CommonR.string.general_tag_system),
                background = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        if (debug) {
            Tag(
                text = stringResource(R.string.appcontrol_tag_debug),
                background = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        if (apkBase) {
            Tag(
                text = stringResource(R.string.appcontrol_tag_apk_base),
                background = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (apkBundle) {
            Tag(
                text = stringResource(R.string.appcontrol_tag_apk_bundle),
                background = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Tag(
    text: String,
    background: Color,
    contentColor: Color,
) {
    Text(
        text = text,
        modifier = Modifier
            .background(background, RoundedCornerShape(4.dp))
            .padding(PaddingValues(horizontal = 6.dp, vertical = 2.dp)),
        color = contentColor,
        fontSize = 11.sp,
        style = MaterialTheme.typography.labelSmall,
    )
}
