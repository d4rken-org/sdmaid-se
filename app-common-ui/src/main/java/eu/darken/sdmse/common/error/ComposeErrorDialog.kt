package eu.darken.sdmse.common.error

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.dialog.SdmDialogButtonBar
import eu.darken.sdmse.common.navigation.NavigationController
import eu.darken.sdmse.common.navigation.NavigationDestination

/**
 * Pluggable customizer for error dialogs. Set this to handle app-specific error types
 * (e.g., IncompleteSetupException, WriteException) with custom localized errors.
 */
var errorDialogCustomizer: ((Throwable, Activity) -> LocalizedError?)? = null

@Composable
fun ComposeErrorDialog(
    throwable: Throwable,
    onDismiss: () -> Unit,
    navController: NavigationController? = null,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val localizedError = errorDialogCustomizer?.invoke(throwable, activity ?: return)
        ?: throwable.localized(context)

    val hasFix = localizedError.fixActionRoute != null || localizedError.fixAction != null
    val hasInfo = localizedError.infoActionRoute != null || localizedError.infoAction != null

    fun dispatchAndDismiss(route: NavigationDestination?, action: ((Activity) -> Unit)?) {
        when {
            route != null -> navController?.goTo(route)
            action != null && activity != null -> action(activity)
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = localizedError.label.get(context),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                SelectionContainer {
                    Text(
                        text = localizedError.description.get(context),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            SdmDialogButtonBar(
                positive = if (hasFix) {
                    SdmDialogAction(
                        label = localizedError.fixActionLabel?.get(context)
                            ?: stringResource(android.R.string.ok),
                        onClick = { dispatchAndDismiss(localizedError.fixActionRoute, localizedError.fixAction) },
                    )
                } else {
                    SdmDialogAction(
                        label = stringResource(android.R.string.ok),
                        onClick = onDismiss,
                    )
                },
                negative = if (hasFix) {
                    SdmDialogAction(
                        label = stringResource(R.string.general_cancel_action),
                        onClick = onDismiss,
                    )
                } else {
                    null
                },
                neutral = if (hasInfo) {
                    SdmDialogAction(
                        label = localizedError.infoActionLabel?.get(context)
                            ?: stringResource(R.string.general_show_details_action),
                        onClick = { dispatchAndDismiss(localizedError.infoActionRoute, localizedError.infoAction) },
                    )
                } else {
                    null
                },
            )
        },
    )
}
