package eu.darken.sdmse.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.net.toUri
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import javax.inject.Inject

@Reusable
class WebpageTool @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun open(address: String) {
        open(context, address)
    }

    companion object {
        fun open(context: Context, address: String) {
            val intent = Intent(Intent.ACTION_VIEW, address.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Android TV has no browser, a system stub consumes browser intents and shows an unhelpful toast.
            val handler = intent.resolveActivity(context.packageManager)
            if (handler != null && handler.packageName in STUB_PACKAGES) {
                log(ERROR) { "Failed to launch. Only stub handler ($handler) available for $address" }
                showNoAppToast(context, address)
                return
            }
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                log(ERROR) { "Failed to launch. No compatible activity for $address" }
                showNoAppToast(context, address)
            } catch (e: SecurityException) {
                // Permission Denial: starting Intent { act=android.intent.action.VIEW dat=https://github.com/...
                // flg=0x10000000 cmp=com.mxtech.videoplayer.pro/com.mxtech.videoplayer.ActivityWebBrowser }
                log(ERROR) { "Failed to launch activity due to $e" }
            }
        }

        private fun showNoAppToast(context: Context, address: String) {
            Handler(Looper.getMainLooper()).post {
                val baseMsg = context.getString(R.string.general_error_no_compatible_app_found_msg).removeSuffix(".")
                Toast.makeText(context, "$baseMsg: $address", Toast.LENGTH_LONG).show()
            }
        }

        private val STUB_PACKAGES = setOf(
            "com.android.tv.frameworkpackagestubs",
            "com.google.android.tv.frameworkpackagestubs",
        )
    }
}