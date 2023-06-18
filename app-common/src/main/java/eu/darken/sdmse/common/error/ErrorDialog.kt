package eu.darken.sdmse.common.error

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.common.R

fun Throwable.asErrorDialogBuilder(
    activity: Activity
): MaterialAlertDialogBuilder {
    val context = activity
    return MaterialAlertDialogBuilder(context).apply {
        val error = this@asErrorDialogBuilder
        val localizedError = error.localized(context)

        setTitle(localizedError.label.get(context))
        setMessage(localizedError.description.get(context))

        if (localizedError.fixAction != null) {
            setPositiveButton(
                localizedError.fixActionLabel?.get(context) ?: context.getString(android.R.string.ok)
            ) { _, _ ->
                localizedError.fixAction.invoke(activity)
            }
            setNegativeButton(R.string.general_cancel_action) { _, _ ->
            }
        } else {
            setPositiveButton(android.R.string.ok) { _, _ ->
            }
        }

        if (localizedError.infoAction != null) {
            setNeutralButton(R.string.general_show_details_action) { _, _ ->
                localizedError.infoAction.invoke(activity)
            }
        }
    }
}