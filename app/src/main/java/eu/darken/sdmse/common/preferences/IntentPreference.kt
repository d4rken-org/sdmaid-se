package eu.darken.sdmse.common.preferences

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.preference.Preference
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag


class IntentPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    @StyleRes defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    override fun setIntent(_intent: Intent?) {
        super.setIntent(_intent)
        _intent?.let {
            intent
            setOnPreferenceClickListener {
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    log(TAG, ERROR) { "Failed to launch $intent: $e" }
                    Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show()
                }
                true
            }
        }
    }

    companion object {
        private val TAG = logTag("IntentPreference")
    }
}