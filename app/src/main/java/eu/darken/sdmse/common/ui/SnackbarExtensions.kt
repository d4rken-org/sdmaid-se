package eu.darken.sdmse.common.ui

import android.widget.TextView
import com.google.android.material.snackbar.Snackbar


fun Snackbar.enableBigText(maxLines: Int = 6) = apply {
    val layout = view as Snackbar.SnackbarLayout
    val textView = layout.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
    textView.maxLines = maxLines
}