package eu.darken.sdmse.common.compose.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

/**
 * Flavor-specific label shown inside [UpgradeBadge]. Provided at the app composition root
 * (MainActivity) so [UpgradeBadge] can resolve the right string without importing app's R class.
 * Default "Pro" covers previews and any composition that forgets to provide an override.
 */
val LocalUpgradeBadgeLabel = staticCompositionLocalOf { "Pro" }

/**
 * Inline "Pro"/"FOSS" chip rendered next to a settings row title when the feature requires
 * an upgrade. Pure presentation — taps are handled by the hosting row.
 */
@Composable
fun UpgradeBadge(
    modifier: Modifier = Modifier,
    label: String = LocalUpgradeBadgeLabel.current,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon is decorative — the adjacent [Text] label is the accessible announcement.
        Icon(
            imageVector = Icons.TwoTone.Stars,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Preview2
@Composable
private fun UpgradeBadgePreview() {
    PreviewWrapper {
        UpgradeBadge()
    }
}

@Preview2
@Composable
private fun UpgradeBadgeFossPreview() {
    PreviewWrapper {
        UpgradeBadge(label = "FOSS")
    }
}
