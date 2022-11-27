package eu.darken.sdmse.common.pkgs.pkgops.installer

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.BuildConfig
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.local.root.DetailedInputSource
import eu.darken.sdmse.common.files.core.local.root.DetailedInputSourceWrap
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.pkgops.installer.InstallerReceiver.InstallEvent
import eu.darken.sdmse.common.pkgs.pkgops.installer.routine.DefaultInstallRoutine
import eu.darken.sdmse.common.pkgs.pkgops.root.PkgOpsClient
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.root.javaroot.JavaRootClient
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.update
import eu.darken.sdmse.processor.core.mm.MMRef
import eu.darken.sdmse.task.core.results.LogEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.plus
import timber.log.Timber
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class APKInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val javaRootClient: JavaRootClient,
    private val pkgOps: PkgOps,
    @AppScope private val appScope: CoroutineScope, // Shouldn't this be processorScope?
    dispatcherProvider: DispatcherProvider,
    private val installRoutineFactory: DefaultInstallRoutine.Factory
) : Progress.Client, Progress.Host, HasSharedResource<Any> {

    private val progressPub = DynamicStateFlow(TAG, appScope) { Progress.Data() }
    override val progress: Flow<Progress.Data> = progressPub.flow
    override fun updateProgress(update: suspend (Progress.Data) -> Progress.Data) = progressPub.updateAsync(onUpdate = update)

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    private val installer = context.packageManager.packageInstaller
    private val installMap = mutableMapOf<String, OnGoingInstall>()

    data class OnGoingInstall(
        val semaphore: Semaphore,
        val rootInstall: Boolean = false,
        val installResult: InstallEvent? = null
    )

    data class Request(
        val packageName: String,
        val baseApk: MMRef,
        val splitApks: List<MMRef>,
        val useRoot: Boolean
    )

    data class Result(
        val success: Boolean,
        val error: Exception? = null
    )

    suspend fun install(request: Request, logListener: ((LogEvent) -> Unit)? = null): Result {
        log(TAG) { "install(request=$request)" }

        updateProgressPrimary(R.string.progress_restoring_apk)
        updateProgressSecondary(R.string.progress_working_label)
        updateProgressCount(Progress.Count.Indeterminate())

        val callbackLock = Semaphore(0)
        synchronized(installMap) {
            require(installMap[request.packageName] == null) {
                "Already installing ${request.packageName}"
            }
            installMap[request.packageName] = OnGoingInstall(callbackLock, true)
        }

        val apkInputs = mutableListOf<DetailedInputSource>()

        try {
            apkInputs.add(
                DetailedInputSourceWrap(
                    request.baseApk.getProps().originalPath as LocalPath,
                    request.baseApk.source.open()
                )
            )

            request.splitApks
                .map {
                    DetailedInputSourceWrap(
                        it.getProps().originalPath as LocalPath,
                        it.source.open()
                    )
                }
                .forEach { apkInputs.add(it) }

            val remoteRequest = object : RemoteInstallRequest.Stub() {
                override fun getPackageName(): String = request.packageName

                override fun getApkInputs(): List<DetailedInputSource> = apkInputs
            }

            if (request.useRoot) {
                javaRootClient.addParent(this)
                javaRootClient.runModuleAction(PkgOpsClient::class.java) {
                    it.install(remoteRequest)
                }
            } else {
                installRoutineFactory.create(rootMode = false).install(remoteRequest)
            }

        } finally {
            apkInputs.forEach {
                try {
                    it.input().close()
                } catch (e: Exception) {
                    log(TAG, WARN) { "Failed to close remote input stream for ${it.path()}\n${e.asLog()}" }
                }
            }
        }

        updateProgressSecondary(R.string.progress_waiting_on_install)
        Timber.tag(TAG).d("Waiting for PackageInstaller callback for %s", request.packageName)
        val noTimeout = callbackLock.tryAcquire(1, 120, TimeUnit.SECONDS)

        val ongoingInstall = installMap.remove(request.packageName)
        requireNotNull(ongoingInstall) { "No OnGoingInstall found for $request" }

        var success = false
        var error: Exception? = null
        if (!noTimeout) {
            error = TimeoutException("Installer did not finish in time: $request")
        } else {
            success = ongoingInstall.installResult!!.code == InstallEvent.Code.SUCCESS
            if (!success) {
                error = IllegalStateException(ongoingInstall.installResult.statusMessage)
            }
        }

        if (success) {
            logListener?.let { listener ->
                val dest = pkgOps.queryAppInfos(request.packageName)!!
                listener(LogEvent(LogEvent.Type.RESTORED, LocalPath.build(dest.sourceDir)))
                dest.splitSourceDirs?.forEach {
                    listener(LogEvent(LogEvent.Type.RESTORED, LocalPath.build(it)))
                }
            }
        }

        if (success) log(TAG) { "APK install successful: $request" }
        else log(TAG, WARN) { "APK install failed for $request\n${error?.asLog()}" }

        return Result(success = success, error = error)
    }

    fun handleEvent(event: InstallEvent) = when (event.code) {
        InstallEvent.Code.SUCCESS, InstallEvent.Code.ERROR -> {
            log(TAG) { "Handling InstallEvent SUCCESS/ERROR: $event" }
            requireNotNull(event.sessionId) { "Event has no packagename: $event" }
            synchronized(installMap) {
                var pkgName = event.packageName
                if (pkgName == null) {
                    log(TAG) { "Event had no packageName, trying sessionId lookup" }
                    Timber.tag(TAG).d("Event had no package name, trying lookup via session id.")
                    requireNotNull(event.sessionId) { "Event had no session ID and no package name, wtf." }
                    val session = installer.allSessions.find { it.sessionId == event.sessionId }
                    requireNotNull(session) { "Can't find matching session for ${event.sessionId}: ${installer.allSessions}" }
                    pkgName = session.appPackageName!!
                }
                installMap.update(pkgName) { it?.copy(installResult = event) }
                installMap.getValue(pkgName).semaphore.release()
            }
        }
        InstallEvent.Code.USER_ACTION -> {
            log(TAG) { "Handling InstallEvent USER_ACTION: $event" }
            val actionIntent = event.userAction!!
            actionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(actionIntent)
        }
    }


    companion object {
        val TAG = logTag("PkgOps", "Installer")

        internal fun createAction(packageName: String): String {
            return "${BuildConfig.APPLICATION_ID}.INSTALLER.CALLBACK:$packageName"
        }
    }
}