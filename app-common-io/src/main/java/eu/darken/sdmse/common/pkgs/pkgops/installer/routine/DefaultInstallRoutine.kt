package eu.darken.sdmse.common.pkgs.pkgops.installer.routine

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.root.DetailedInputSource
import eu.darken.sdmse.common.files.core.local.root.inputStream
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.pkgops.installer.InstallRoutine
import eu.darken.sdmse.common.pkgs.pkgops.installer.RemoteInstallRequest
import eu.darken.sdmse.common.reflection.getPrivateProperty
import eu.darken.sdmse.common.reflection.setPrivateProperty

class DefaultInstallRoutine @AssistedInject constructor(
    @ApplicationContext private val context: Context,
    @Assisted private val rootMode: Boolean,
) : InstallRoutine {
    private val installer = context.packageManager.packageInstaller

    /**
     * May be called with ROOT, WHOOP WHOOP, SYSTEM UID 0, or NOT?
     */
    override fun install(request: RemoteInstallRequest): Int {
        log(TAG) { "Installing ${request.packageName}" }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(request.packageName)

            @SuppressLint("NewApi")
            if (hasApiLevel(Build.VERSION_CODES.P)) {
                setInstallReason(PackageManager.INSTALL_REASON_USER)
            }
            if (rootMode) {
                try {
                    installFlags = installFlags or PM_INSTALL_ALLOW_TEST
                } catch (e: Exception) {
                    log(TAG, WARN) { "Setting INSTALL_ALLOW_TEST failed\n${e.asLog()}" }
                }
            }
        }

        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        request.apkInputs.map { DetailedInputSource.Stub.asInterface(it as IBinder?) }.forEach { source ->
            val label = source.path().name
            log(TAG, VERBOSE) { "Writing $label (${source.length()})" }

            session.openWrite(label, 0, -1).use { output ->
                source.input().inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }

            log(TAG, VERBOSE) { "Finished writing $label" }
        }

        val pi = createInstallCallback(context, request.packageName)
        log(TAG) { "commit(callback=$pi)" }

        if (rootMode) {
            session.commit(pi.intentSender)
            // Alternative, normal commit within root service works
            // Cmd.builder("pm install-commit $sessionId").submit(RxCmdShell.builder().root(true).build()).blockingGet()
        } else {
            session.commit(pi.intentSender)
        }

        return sessionId
    }

    @AssistedFactory
    interface Factory {
        fun create(rootMode: Boolean): DefaultInstallRoutine
    }

    companion object {
        val TAG = logTag("PkgOps", "Installer", "DefaultRoutine")
        private const val PM_INSTALL_ALLOW_TEST: Int = 0x00000004
    }
}

internal var PackageInstaller.SessionParams.installFlags: Int
    get() {
        return getPrivateProperty<PackageInstaller.SessionParams, Int>("installFlags")!!
    }
    set(value) {
        setPrivateProperty("installFlags", value)
    }