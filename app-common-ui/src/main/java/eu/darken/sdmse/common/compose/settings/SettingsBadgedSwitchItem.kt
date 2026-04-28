package eu.darken.sdmse.common.compose.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Build
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

/**
 * Opaque "this row is gated" marker. Each value carries the visual cue (badge icon + tint)
 * the row should show. Consumers decide what happens on badge click via [onBadgeClick].
 *
 * Pro-gated rows no longer use this mechanism — they render an inline [UpgradeBadge] next
 * to the title instead. Use `requiresUpgrade` + `onUpgrade` on [SettingsSwitchItem] or
 * [SettingsPreferenceItem] for those.
 */
sealed class SettingGate(val badgeIcon: ImageVector) {
    data object SetupRequired : SettingGate(Icons.TwoTone.Build)
}

/**
 * Switch row that suppresses DataStore writes while [gate] is non-null — taps invoke
 * [onBadgeClick] instead of [onCheckedChange]. Mirrors the legacy BadgedCheckboxPreference
 * behavior: the underlying control appears disabled, and a badge icon overlays the switch.
 *
 * Pro gating ([requiresUpgrade] + [onUpgrade]) takes precedence over [gate] — when both are
 * specified for the same row (e.g. multi-user apps that also need a root setup), Pro gating
 * is the one surfaced to the user because upgrading unblocks the Setup affordance anyway.
 */
@Composable
fun SettingsBadgedSwitchItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onBadgeClick: () -> Unit,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    gate: SettingGate? = null,
    enabled: Boolean = true,
    requiresUpgrade: Boolean = false,
    onUpgrade: () -> Unit = {},
) {
    if (requiresUpgrade || gate == null) {
        SettingsSwitchItem(
            icon = icon,
            iconPainter = iconPainter,
            title = title,
            subtitle = subtitle,
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            requiresUpgrade = requiresUpgrade,
            onUpgrade = onUpgrade,
        )
        return
    }

    // Keep the row itself `enabled = true` so the badge click fires; the legacy
    // BadgedCheckboxPreference achieved the same by stacking a clickable overlay on
    // top of a disabled checkbox. The visual "disabled" cue is applied only to the
    // trailing Switch — row title/subtitle stay at full alpha so the badge affordance
    // is legible.
    SettingsBaseItem(
        icon = icon,
        iconPainter = iconPainter,
        title = title,
        onClick = onBadgeClick,
        modifier = modifier,
        subtitle = subtitle,
        enabled = true,
        trailingContent = {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .wrapContentSize(),
                contentAlignment = Alignment.Center,
            ) {
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = false,
                )
                Icon(
                    imageVector = gate.badgeIcon,
                    contentDescription = stringResource(CommonR.string.general_set_up_action),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}

@Preview2
@Composable
private fun SettingsBadgedSwitchItemUngatedPreview() {
    PreviewWrapper {
        SettingsBadgedSwitchItem(
            icon = Icons.TwoTone.Settings,
            title = "Ungated setting",
            subtitle = "Behaves like a normal switch when gate is null",
            checked = true,
            onCheckedChange = {},
            onBadgeClick = {},
        )
    }
}

@Preview2
@Composable
private fun SettingsBadgedSwitchItemSetupRequiredPreview() {
    PreviewWrapper {
        SettingsBadgedSwitchItem(
            icon = Icons.TwoTone.Settings,
            title = "Needs setup",
            subtitle = "Switch is suppressed; tap opens setup",
            checked = false,
            onCheckedChange = {},
            onBadgeClick = {},
            gate = SettingGate.SetupRequired,
        )
    }
}

