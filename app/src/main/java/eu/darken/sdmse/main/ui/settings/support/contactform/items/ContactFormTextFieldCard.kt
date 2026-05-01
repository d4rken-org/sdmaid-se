package eu.darken.sdmse.main.ui.settings.support.contactform.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R

private const val MAX_TEXT_LENGTH = 5000

/**
 * Shared text-input card used by [ContactFormDescriptionCard] and [ContactFormExpectedCard]. The
 * two surfaces differ only in label, hint, and minimum line count; everything else (word-count
 * tinting, 5000-char truncation, outlined field, keyboard options) is identical, so the logic
 * lives here rather than being duplicated.
 */
@Composable
internal fun ContactFormTextFieldCard(
    value: String,
    labelRes: Int,
    hint: String,
    wordCount: Int,
    minWords: Int,
    minLines: Int,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val counterColor = when {
        wordCount == 0 -> colors.onSurfaceVariant
        wordCount >= minWords -> colors.primary
        else -> colors.error
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(if (it.length > MAX_TEXT_LENGTH) it.take(MAX_TEXT_LENGTH) else it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(labelRes)) },
                minLines = minLines,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                ),
                supportingText = {
                    Text(
                        text = pluralStringResource(
                            R.plurals.support_contact_word_count,
                            wordCount,
                            wordCount,
                            minWords,
                        ),
                        color = counterColor,
                    )
                },
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
