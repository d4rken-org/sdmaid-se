package eu.darken.sdmse.main.ui.settings.support.contactform.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Segment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.ui.settings.support.contactform.SupportContactFormViewModel.Category

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactFormCategoryCard(
    selected: Category,
    onChange: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Segment,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.support_contact_category_label),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
            ) {
                CategoryChip(
                    label = stringResource(R.string.support_contact_category_question_label),
                    selected = selected == Category.QUESTION,
                    onClick = { onChange(Category.QUESTION) },
                )
                CategoryChip(
                    label = stringResource(R.string.support_contact_category_feature_label),
                    selected = selected == Category.FEATURE,
                    onClick = { onChange(Category.FEATURE) },
                )
                CategoryChip(
                    label = stringResource(R.string.support_contact_category_bug_label),
                    selected = selected == Category.BUG,
                    onClick = { onChange(Category.BUG) },
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.padding(end = 8.dp),
        colors = FilterChipDefaults.filterChipColors(),
    )
}

@Preview2
@Composable
private fun ContactFormCategoryCardPreview() {
    PreviewWrapper {
        ContactFormCategoryCard(selected = Category.BUG, onChange = {})
    }
}
