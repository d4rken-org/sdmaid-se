package eu.darken.sdmse.common.error

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.navigation.NavigationController

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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (hasInfo) {
                    TextButton(
                        onClick = {
                            val route = localizedError.infoActionRoute
                            val action = localizedError.infoAction
                            when {
                                route != null -> navController?.goTo(route)
                                action != null && activity != null -> action(activity)
                            }
                            onDismiss()
                        },
                    ) {
                        Text(
                            localizedError.infoActionLabel?.get(context)
                                ?: stringResource(R.string.general_show_details_action)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (hasFix) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.general_cancel_action))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val route = localizedError.fixActionRoute
                            val action = localizedError.fixAction
                            when {
                                route != null -> navController?.goTo(route)
                                action != null && activity != null -> action(activity)
                            }
                            onDismiss()
                        },
                    ) {
                        Text(
                            localizedError.fixActionLabel?.get(context)
                                ?: stringResource(android.R.string.ok)
                        )
                    }
                } else {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        },
    )
}
