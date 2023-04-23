package eu.darken.sdmse.appcontrol.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.darken.sdmse.common.pkgs.getSettingsIntent

fun AppInfo.createSystemSettingsIntent(context: Context): Intent = pkg.getSettingsIntent(context).apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

fun AppInfo.createGooglePlayIntent(context: Context): Intent {
    val targetIntent = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=${pkg.packageName}")
        },
        0
    )
        .firstOrNull { it.activityInfo.applicationInfo.packageName == "com.android.vending" }
        ?.let {
            val activityInfo = it.activityInfo
            ComponentName(activityInfo.applicationInfo.packageName, activityInfo.name)
        }
        ?.let {
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=${pkg.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                // if the Google Play was already open in a search result this make sure it still go to the app page you requested
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

    return targetIntent ?: Intent().apply {
        data = Uri.parse("https://play.google.com/store/apps/details?id=${pkg.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
