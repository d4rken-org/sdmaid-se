package eu.darken.sdmse.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

/**
 * Highlighted hint for setup states the user can't (fully) fix themselves, e.g. OS restrictions or device
 * limitations. Pass the available actions (help, settings, ...) as buttons; they share the row equally.
 */
@Composable
internal fun SetupLimitationBox(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    body2: String? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 32.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.TwoTone.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Unspecified,
        )
        body2?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Unspecified,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions()
        }
    }
}

@Preview2
@Composable
private fun SetupLimitationBoxPreview() {
    PreviewWrapper {
        SetupLimitationBox(
            title = "Possible device limitation",
            body = "The system returned an incomplete list of installed apps, even though the required permissions appear to be granted.",
            body2 = "Some devices never provide a complete app list. To protect your data, app-related tools stay disabled.",
        ) {
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                Text("Help")
            }
            Button(onClick = {}, modifier = Modifier.weight(1f)) {
                Text("Open system settings")
            }
        }
    }
}
