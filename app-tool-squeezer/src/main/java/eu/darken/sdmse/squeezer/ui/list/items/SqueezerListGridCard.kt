package eu.darken.sdmse.squeezer.ui.list.items

import android.text.format.Formatter
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.squeezer.R
import eu.darken.sdmse.squeezer.core.CompressibleImage

@Composable
internal fun SqueezerListGridCard(
    modifier: Modifier = Modifier,
    image: CompressibleImage,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onPreviewTap: () -> Unit,
) {
    val context = LocalContext.current
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    Card(
        modifier = modifier
            .padding(4.dp)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = CardDefaults.shape,
            )
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress,
            ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                FilePreviewImage(
                    lookup = image.lookup,
                    modifier = Modifier.fillMaxSize(),
                )
                FilledTonalIconButton(
                    onClick = onPreviewTap,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.Search,
                        contentDescription = null,
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = Formatter.formatShortFileSize(context, image.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val savings = image.estimatedSavings
                Text(
                    text = if (savings != null && savings > 0) {
                        stringResource(
                            R.string.squeezer_estimated_savings_format,
                            Formatter.formatShortFileSize(context, savings),
                        )
                    } else {
                        stringResource(R.string.squeezer_no_savings_expected)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
