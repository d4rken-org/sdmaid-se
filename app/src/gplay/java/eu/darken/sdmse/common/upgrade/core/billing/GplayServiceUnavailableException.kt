package eu.darken.sdmse.common.upgrade.core.billing

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

class GplayServiceUnavailableException(cause: Throwable) :
    BillingException("Google Play services are unavailable.", cause), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.upgrades_gplay_unavailable_error_title.toCaString(),
        description = R.string.upgrades_gplay_unavailable_error_description.toCaString(),
        fixActionLabel = "Google Play".toCaString(),
        fixAction = { activity ->
            try {
                val intent = Intent().apply {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.fromParts("package", GPLAY_PKG, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                activity.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                log(ERROR) { "Can't launch settings intent for Google Play: $e" }
                Toast.makeText(activity, "Google Play is not installed", Toast.LENGTH_SHORT).show()
            }
        }
    )

    companion object {
        private const val GPLAY_PKG = "com.android.vending"
    }
}