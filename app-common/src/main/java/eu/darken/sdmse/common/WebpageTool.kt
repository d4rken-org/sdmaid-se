package eu.darken.sdmse.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(address)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            log(ERROR) { "Failed to launch. No compatible activity!" }
        } catch (e: SecurityException) {
            // Permission Denial: starting Intent { act=android.intent.action.VIEW dat=https://github.com/...
            // flg=0x10000000 cmp=com.mxtech.videoplayer.pro/com.mxtech.videoplayer.ActivityWebBrowser }
            log(ERROR) { "Failed to launch activity due to $e" }
        }
    }

}