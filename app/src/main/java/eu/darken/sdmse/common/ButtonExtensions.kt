package eu.darken.sdmse.common

import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.reflection.getField

fun CompoundButton.setChecked2(checked: Boolean, animate: Boolean = true) {
    val currentListener = getOnCheckedChangeListener()
    if (currentListener != null) setOnCheckedChangeListener(null)

    isChecked = checked
    if (!animate) jumpDrawablesToCurrentState()

    if (currentListener != null) setOnCheckedChangeListener(currentListener)
}

fun CompoundButton.getOnCheckedChangeListener(): OnCheckedChangeListener? = try {
    val field = CompoundButton::class.getField("mOnCheckedChangeListener")
    field.get(this) as? OnCheckedChangeListener
} catch (e: Exception) {
    log(WARN) { "Failed to access CompoundButton.mOnCheckedChangeListener: ${e.asLog()}" }
    null
}