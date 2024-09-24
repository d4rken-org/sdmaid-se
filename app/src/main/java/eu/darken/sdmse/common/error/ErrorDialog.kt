package eu.darken.sdmse.common.error

import android.app.Activity
import android.util.TypedValue
import androidx.navigation.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.files.WriteException
import eu.darken.sdmse.exclusion.ui.editor.path.PathExclusionEditorOptions
import eu.darken.sdmse.exclusion.ui.editor.path.PathExclusionFragmentArgs

fun Throwable.asErrorDialogBuilder(
    context: Activity
): MaterialAlertDialogBuilder {
    return MaterialAlertDialogBuilder(context).apply {
        val error = this@asErrorDialogBuilder
        val localizedError = error.localized(context)

        setTitle(localizedError.label.get(context))

        val messageView = MaterialTextView(context).apply {
            text = localizedError.description.get(context)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Caption)
            setTextIsSelectable(true)

            val paddingHorizontal = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics
            ).toInt()
            val paddingVertical = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics
            ).toInt()
            setPadding(
                paddingHorizontal,
                paddingVertical,
                paddingHorizontal,
                0,
            )
        }
        setView(messageView)

        if (localizedError.fixAction != null) {
            setPositiveButton(
                localizedError.fixActionLabel?.get(context) ?: context.getString(android.R.string.ok)
            ) { _, _ ->
                localizedError.fixAction!!.invoke(context)
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
                    localizedError.infoAction!!.invoke(context)
                }
            }

            this@asErrorDialogBuilder is WriteException && path != null -> {
                setNeutralButton(eu.darken.sdmse.R.string.exclusion_create_action) { _, _ ->
                    context.findNavController(eu.darken.sdmse.R.id.nav_host).navigate(
                        resId = eu.darken.sdmse.R.id.goToPathExclusionEditor,
                        args = PathExclusionFragmentArgs(
                            initial = PathExclusionEditorOptions(targetPath = path!!)
                        ).toBundle()
                    )
                }
            }
        }
    }
}