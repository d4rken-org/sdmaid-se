package eu.darken.sdmse.common.error

import android.app.Activity
import androidx.navigation.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.files.WriteException
import eu.darken.sdmse.exclusion.ui.editor.path.PathExclusionEditorOptions
import eu.darken.sdmse.exclusion.ui.editor.path.PathExclusionFragmentArgs

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
                localizedError.fixAction!!.invoke(activity)
            }
            setNegativeButton(R.string.general_cancel_action) { _, _ ->
            }
        } else {
            setPositiveButton(android.R.string.ok) { _, _ ->
            }
        }

        when {
            localizedError.infoAction != null -> {
                setNeutralButton(R.string.general_show_details_action) { _, _ ->
                    localizedError.infoAction!!.invoke(activity)
                }
            }

            this@asErrorDialogBuilder is WriteException -> {
                setNeutralButton(eu.darken.sdmse.R.string.exclusion_create_action) { _, _ ->
                    activity.findNavController(eu.darken.sdmse.R.id.nav_host).navigate(
                        resId = eu.darken.sdmse.R.id.goToPathExclusionEditor,
                        args = PathExclusionFragmentArgs(
                            initial = PathExclusionEditorOptions(targetPath = this@asErrorDialogBuilder.path)
                        ).toBundle()
                    )
                }
            }
        }
    }
}