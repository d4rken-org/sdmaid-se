package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.sieve.SieveCriterium

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun TaggedChip(
    criterium: SieveCriterium,
    onRemove: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InputChip(
        selected = false,
        onClick = {},
        modifier = modifier.combinedClickable(onClick = {}, onLongClick = onLongClick),
        label = { Text(criteriumValue(criterium)) },
        leadingIcon = {
            CriteriumLeadingIcon(
                criterium = criterium,
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
        trailingIcon = {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(InputChipDefaults.IconSize),
                )
            }
        },
    )
}

@Composable
private fun CriteriumLeadingIcon(
    criterium: SieveCriterium,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = criteriumIcon(criterium),
        contentDescription = null,
        modifier = modifier,
    )
}
