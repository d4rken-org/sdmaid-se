package eu.darken.sdmse.appcleaner.core.automation.specs.hyperos

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import eu.darken.sdmse.R
import eu.darken.sdmse.automation.core.errors.InvalidSystemStateException
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.error.HasLocalizedError
import eu.darken.sdmse.common.error.LocalizedError

class SecurityCenterMissingPermissionException(
    message: String = "App `com.miui.securitycenter` is missing the GET_USAGE_STATS permission."
) : InvalidSystemStateException(message), HasLocalizedError {
    override fun getLocalizedError() = LocalizedError(
        throwable = this,
        label = R.string.appcleaner_automation_error_securitycenter_permission_title.toCaString(),
        description = R.string.appcleaner_automation_error_securitycenter_permission_body.toCaString(),
        fixActionLabel = eu.darken.sdmse.common.R.string.general_grant_access_action.toCaString(),
        fixAction = {
            try {
                it.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (e: Exception) {
                e.asLog()
            }
        },
        infoActionLabel = eu.darken.sdmse.common.R.string.general_help_action.toCaString(),
        infoAction = {
            try {
                val url = Uri.parse("https://github.com/d4rken-org/sdmaid-se/wiki/AppCleaner#commiuisecuritycenter")
                val intent = Intent(Intent.ACTION_VIEW, url).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                it.startActivity(intent)
            } catch (e: Exception) {
                e.asLog()
            }
        }
    )
}
