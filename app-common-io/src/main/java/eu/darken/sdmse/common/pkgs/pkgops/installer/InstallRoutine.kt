package eu.darken.sdmse.common.pkgs.pkgops.installer

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import eu.darken.sdmse.BuildConfig

interface InstallRoutine {
    /**
     * Returns the session id started by this install request
     */
    fun install(request: RemoteInstallRequest): Int

    fun createInstallCallback(context: Context, packageName: String): PendingIntent {
        val installIntent = Intent()
        installIntent.component = ComponentName(BuildConfig.APPLICATION_ID, InstallerReceiver::class.java.name)
        installIntent.action = APKInstaller.createAction(packageName)
        installIntent.setPackage(BuildConfig.APPLICATION_ID) // Context could be system when called via root
        return PendingIntent.getBroadcast(context, 123, installIntent, PendingIntent.FLAG_UPDATE_CURRENT)
    }
}