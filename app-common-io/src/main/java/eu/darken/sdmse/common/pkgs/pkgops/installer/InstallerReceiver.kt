package eu.darken.sdmse.common.pkgs.pkgops.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.pkgops.installer.InstallerReceiver.InstallEvent.Code
import javax.inject.Inject

@AndroidEntryPoint
class InstallerReceiver : BroadcastReceiver() {

    @Inject lateinit var installer: APKInstaller

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG, VERBOSE) { "onReceive(context=$context, intent=$intent)" }

        val code = when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_SUCCESS -> Code.SUCCESS
            PackageInstaller.STATUS_PENDING_USER_ACTION -> Code.USER_ACTION
            else -> Code.ERROR
        }

        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)

        val event = InstallEvent(
            code = code,
            statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
            packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME),
            sessionId = if (sessionId == -1) null else sessionId,
            userAction = intent.getParcelableExtra(Intent.EXTRA_INTENT)
        )

        log(TAG) { "Processing event $event" }
        installer.handleEvent(event)
    }

    data class InstallEvent(
        val code: Code,
        val statusMessage: String?,
        val packageName: String?,
        val sessionId: Int?,
        val userAction: Intent?
    ) {
        enum class Code {
            SUCCESS, ERROR, USER_ACTION
        }
    }

    companion object {
        val TAG = logTag("PkgOps", "Installer", "Receiver")
    }
}