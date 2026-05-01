package eu.darken.sdmse.main.ui.settings.support.contactform.items

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.ui.settings.support.contactform.SupportContactFormViewModel.Category

@Composable
fun ContactFormDescriptionCard(
    description: String,
    wordCount: Int,
    minWords: Int,
    category: Category,
    onDescriptionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hintRes = when (category) {
        Category.BUG -> R.string.support_contact_description_bug_hint
        Category.FEATURE -> R.string.support_contact_description_feature_hint
        Category.QUESTION -> R.string.support_contact_description_question_hint
    }
    ContactFormTextFieldCard(
        value = description,
        labelRes = R.string.support_contact_description_label,
        hint = stringResource(hintRes),
        wordCount = wordCount,
        minWords = minWords,
        minLines = 4,
        onValueChange = onDescriptionChange,
        modifier = modifier,
    )
}

@Preview2
@Composable
private fun ContactFormDescriptionCardPreview() {
    PreviewWrapper {
        ContactFormDescriptionCard(
            description = "Sample description",
            wordCount = 2,
            minWords = 20,
            category = Category.BUG,
            onDescriptionChange = {},
        )
    }
}
