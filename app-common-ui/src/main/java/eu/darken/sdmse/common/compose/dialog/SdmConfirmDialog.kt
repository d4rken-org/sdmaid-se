package eu.darken.sdmse.common.compose.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

/**
 * Reusable confirmation dialog that preserves the classic Material alert dialog button semantics:
 * - Positive action (e.g. confirm/delete) sits on the trailing edge (right in LTR).
 * - Negative action (e.g. cancel) sits next to the positive action, on its leading side.
 * - Neutral action (e.g. show details) sits on the leading edge (left in LTR), opposite the
 *   positive/negative pair.
 */
@Composable
fun SdmConfirmDialog(
    message: String,
    onDismissRequest: () -> Unit,
    positive: SdmDialogAction,
    modifier: Modifier = Modifier,
    title: String? = null,
    negative: SdmDialogAction? = null,
    neutral: SdmDialogAction? = null,
) {
    SdmConfirmDialog(
        onDismissRequest = onDismissRequest,
        positive = positive,
        modifier = modifier,
        title = title,
        negative = negative,
        neutral = neutral,
        content = { Text(message) },
    )
}

@Composable
fun SdmConfirmDialog(
    onDismissRequest: () -> Unit,
    positive: SdmDialogAction,
    modifier: Modifier = Modifier,
    title: String? = null,
    negative: SdmDialogAction? = null,
    neutral: SdmDialogAction? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = title?.let { { Text(it) } },
        text = content,
        confirmButton = {
            SdmDialogButtonBar(
                positive = positive,
                negative = negative,
                neutral = neutral,
            )
        },
    )
}

data class SdmDialogAction(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Preview2
@Composable
private fun SdmConfirmDialogAllActionsPreview() {
    PreviewWrapper {
        SdmConfirmDialog(
            title = stringResource(CommonR.string.general_delete_confirmation_title),
            message = stringResource(CommonR.string.general_delete_confirmation_message_x, "example.apk"),
            onDismissRequest = {},
            positive = SdmDialogAction(
                label = stringResource(CommonR.string.general_delete_action),
                onClick = {},
            ),
            negative = SdmDialogAction(
                label = stringResource(CommonR.string.general_cancel_action),
                onClick = {},
            ),
            neutral = SdmDialogAction(
                label = stringResource(CommonR.string.general_show_details_action),
                onClick = {},
            ),
        )
    }
}

@Preview2
@Composable
private fun SdmConfirmDialogPositiveNegativePreview() {
    PreviewWrapper {
        SdmConfirmDialog(
            title = stringResource(CommonR.string.general_delete_confirmation_title),
            message = stringResource(CommonR.string.general_delete_confirmation_message_x, "example.apk"),
            onDismissRequest = {},
            positive = SdmDialogAction(
                label = stringResource(CommonR.string.general_delete_action),
                onClick = {},
            ),
            negative = SdmDialogAction(
                label = stringResource(CommonR.string.general_cancel_action),
                onClick = {},
            ),
        )
    }
}

@Preview2
@Composable
private fun SdmConfirmDialogPositiveOnlyPreview() {
    PreviewWrapper {
        SdmConfirmDialog(
            title = "Heads-up",
            message = "Confirmation acknowledged.",
            onDismissRequest = {},
            positive = SdmDialogAction(
                label = stringResource(android.R.string.ok),
                onClick = {},
            ),
        )
    }
}
