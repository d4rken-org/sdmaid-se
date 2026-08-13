package eu.darken.sdmse.common.error

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import eu.darken.sdmse.common.compose.dialog.SdmAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.dialog.SdmDialogButtonBar
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
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

    // Keyed on the throwable, not the LocalizedError: the latter is rebuilt (with fresh action
    // lambdas, so never equal) on every recomposition, which would wipe the message immediately.
    var actionError by remember(throwable) { mutableStateOf<CaString?>(null) }

    // errorMessage is per-dispatch, NOT read from localizedError: this function serves both the fix
    // and the info button, and fixActionErrorMessage describes only the fix action's failure. Each
    // call site passes its own copy (or none), so no button can ever surface another one's message.
    fun dispatchAndDismiss(
        route: NavigationDestination?,
        action: ((Activity) -> Unit)?,
        errorMessage: CaString? = null,
    ) {
        // Error actions are arbitrary third-party code (intent launches, navigation): a throw here
        // would crash the UI thread from inside a click handler, and skipping onDismiss() would
        // leave the dialog latched on the current error with no way out.
        try {
            when {
                route != null -> navController?.goTo(route)
                action != null && activity != null -> action(activity)
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Error action failed: ${e.asLog()}" }
            // A dispatch that ships its own failure copy keeps the dialog open and shows it inline
            // (no length cap, unlike a Toast). Never latched: the dismiss button stays available.
            errorMessage?.let {
                actionError = it
                return
            }
        }
        onDismiss()
    }

    SdmAlertDialog(
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
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                actionError?.let {
                    SelectionContainer {
                        Text(
                            text = it.get(context),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            SdmDialogButtonBar(
                positive = if (hasFix) {
                    SdmDialogAction(
                        label = localizedError.fixActionLabel?.get(context)
                            ?: stringResource(android.R.string.ok),
                        onClick = {
                            dispatchAndDismiss(
                                route = localizedError.fixActionRoute,
                                action = localizedError.fixAction,
                                errorMessage = localizedError.fixActionErrorMessage,
                            )
                        },
                    )
                } else {
                    SdmDialogAction(
                        label = stringResource(android.R.string.ok),
                        onClick = onDismiss,
                    )
                },
                negative = if (hasFix) {
                    SdmDialogAction(
                        label = stringResource(R.string.general_dismiss_action),
                        onClick = onDismiss,
                    )
                } else {
                    null
                },
                neutral = if (hasInfo) {
                    SdmDialogAction(
                        label = localizedError.infoActionLabel?.get(context)
                            ?: stringResource(R.string.general_show_details_action),
                        // No errorMessage: the info action has no failure copy of its own, and it
                        // must never borrow the fix action's.
                        onClick = {
                            dispatchAndDismiss(
                                route = localizedError.infoActionRoute,
                                action = localizedError.infoAction,
                            )
                        },
                    )
                } else {
                    null
                },
            )
        },
    )
}

private val TAG = logTag("Error", "Dialog", "Compose")
