package eu.darken.sdmse.stats.ui.pkgs.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.stats.core.AffectedPkg
import eu.darken.sdmse.stats.core.icon
import eu.darken.sdmse.stats.ui.pkgs.AffectedPkgsViewModel

@Composable
fun AffectedPkgRow(
    modifier: Modifier = Modifier,
    row: AffectedPkgsViewModel.Row,
) {
    val context = LocalContext.current
    val pkgLabel = row.installedPkg?.label?.get(context)
    val pkgName = row.affectedPkg.pkgId.name
    val displayText = pkgLabel?.let { "$it ($pkgName)" } ?: pkgName

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = row.affectedPkg.action.icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = displayText,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(modifier = Modifier.size(24.dp)) {
            row.installedPkg?.let { pkg ->
                AsyncImage(
                    modifier = Modifier.size(24.dp),
                    model = pkg,
                    contentDescription = null,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun AffectedPkgRowPreview() {
    PreviewWrapper {
        AffectedPkgRow(
            row = AffectedPkgsViewModel.Row(
                affectedPkg = object : AffectedPkg {
                    override val reportId = java.util.UUID.randomUUID()
                    override val action = AffectedPkg.Action.DELETED
                    override val pkgId = Pkg.Id("com.example.app")
                },
                installedPkg = null,
            ),
        )
    }
}
