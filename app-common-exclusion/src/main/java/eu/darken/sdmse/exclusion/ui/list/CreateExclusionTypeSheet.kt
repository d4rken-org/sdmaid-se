package eu.darken.sdmse.exclusion.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.FilterAlt
import androidx.compose.material.icons.twotone.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.exclusion.R

@Composable
internal fun CreateExclusionTypeSheet(
    onPickPackage: () -> Unit = {},
    onPickPath: () -> Unit = {},
    onPickSegment: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.exclusion_create_action),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        ListItem(
            modifier = Modifier.clickable(onClick = onPickPackage),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(Icons.TwoTone.Apps, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.exclusion_type_package)) },
            supportingContent = { Text(stringResource(R.string.exclusion_create_pkg_hint)) },
        )
        ListItem(
            modifier = Modifier.clickable(onClick = onPickPath),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(Icons.TwoTone.FolderOpen, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.exclusion_type_path)) },
            supportingContent = { Text(stringResource(R.string.exclusion_create_path_hint)) },
        )
        ListItem(
            modifier = Modifier.clickable(onClick = onPickSegment),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(Icons.TwoTone.FilterAlt, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.exclusion_type_segment)) },
            supportingContent = { Text(stringResource(R.string.exclusion_create_segment_hint)) },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Preview2
@Composable
private fun CreateExclusionTypeSheetPreview() {
    PreviewWrapper {
        CreateExclusionTypeSheet()
    }
}
