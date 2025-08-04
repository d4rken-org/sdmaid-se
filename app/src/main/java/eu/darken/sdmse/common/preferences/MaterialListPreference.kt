package eu.darken.sdmse.common.preferences

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.preference.ListPreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MaterialListPreference : ListPreferenceDialogFragmentCompat() {

    private var clickedButtonId = -1

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireActivity()).setTitle(preference.dialogTitle).apply {
            setIcon(preference.dialogIcon)
            setPositiveButton(preference.positiveButtonText, this@MaterialListPreference)
            setNegativeButton(preference.negativeButtonText, this@MaterialListPreference)
        }

        val contentView = context?.let { onCreateDialogView(it) }
        if (contentView != null) {
            onBindDialogView(contentView)
            builder.setView(contentView)
        } else {
            builder.setMessage(preference.dialogMessage)
        }
        onPrepareDialogBuilder(builder)
        return builder.create()
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        clickedButtonId = which
    }

    private var closedViaDismiss = false

    override fun onDismiss(dialog: DialogInterface) {
        closedViaDismiss = true
        super.onDismiss(dialog)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (closedViaDismiss) {
            closedViaDismiss = false
            super.onDialogClosed(clickedButtonId == DialogInterface.BUTTON_POSITIVE)
        } else {
            super.onDialogClosed(positiveResult)
        }
    }
}