package eu.darken.sdmse.main.ui.settings.support.contactform.items

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

@Composable
fun ContactFormExpectedCard(
    expected: String,
    wordCount: Int,
    minWords: Int,
    onExpectedChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ContactFormTextFieldCard(
        value = expected,
        labelRes = R.string.support_contact_expected_label,
        hint = stringResource(R.string.support_contact_expected_hint),
        wordCount = wordCount,
        minWords = minWords,
        minLines = 3,
        onValueChange = onExpectedChange,
        modifier = modifier,
    )
}

@Preview2
@Composable
private fun ContactFormExpectedCardPreview() {
    PreviewWrapper {
        ContactFormExpectedCard(
            expected = "",
            wordCount = 0,
            minWords = 10,
            onExpectedChange = {},
        )
    }
}
