package eu.darken.sdmse.deduplicator.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.deduplicator.R

@Composable
internal fun DetectionCaveatCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.TwoTone.Warning,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.deduplicator_detection_method_similarity_caveat),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview2
@Composable
private fun DetectionCaveatCardPreview() {
    PreviewWrapper {
        DetectionCaveatCard()
    }
}
