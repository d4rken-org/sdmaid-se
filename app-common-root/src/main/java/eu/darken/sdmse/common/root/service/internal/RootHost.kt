package eu.darken.sdmse.common.root.service.internal

import android.annotation.SuppressLint
import android.content.Context
import android.os.Debug
import android.os.Looper
import android.util.Base64
import android.util.Log
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.LogCatLogger
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.parcel.unmarshall
import kotlinx.coroutines.*
import java.lang.reflect.Method
import kotlin.system.exitProcess

@SuppressLint("PrivateApi")
abstract class RootHost(
    val iTag: String,
    private val _args: List<String>
) {

    val rootHostScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var options: RootHostOptions

    val ourPkgName: String
        get() = options.packageName
    val pairingCode: String
        get() = options.pairingCode
    val isDebug: Boolean
        get() = options.isDebug

    @SuppressLint("LogNotTimber")
    fun start() = try {
        Log.d(iTag, "start(): RootHost args=${_args}")

        val optionsBase64 = _args.single().let {
            require(it.startsWith("$OPTIONS_KEY=")) { "Unexpected options format: $_args" }
            it.removePrefix("$OPTIONS_KEY=")
        }

        Log.d(iTag, "start(): unmarshalling $optionsBase64")
        options = Base64.decode(optionsBase64, 0).unmarshall()
        Log.d(iTag, "start(): options=$options")

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log(iTag, ERROR) { "Uncaught exception within JavaRootHost: ${throwable.asLog()}" }
            if (oldHandler != null) oldHandler.uncaughtException(thread, throwable)
            else exitProcess(1)
        }

        Bugs.isDebug = options.isDebug
        Bugs.isTrace = options.isTrace

        if (isDebug) {
            Logging.install(LogCatLogger())
            Log.i(iTag, "Debug logger installed")
            log(iTag, INFO) { "Debug logger installed" }
        }

        if (options.isDebug) {
            setAppName("$ourPkgName:rootHost")

            val waitStart = System.currentTimeMillis()
            while (options.waitForDebugger && !Debug.isDebuggerConnected()) {
                val elapsed = System.currentTimeMillis() - waitStart
                log(iTag, VERBOSE) { "Waiting for debugger (${elapsed / 1000}s)" }

                if (elapsed > 60 * 1000) {
                    log(iTag, WARN) { "Timeout while waiting for debugger!" }
                    break
                }

                try {
                    Thread.sleep(1000)
                } catch (ignored: InterruptedException) {
                }
            }
        }

        runBlocking {
            onInit()
            onExecute()
        }
    } catch (e: Throwable) {
        Log.e(iTag, "Failed to run RootHost.", e)
        throw e
    } finally {
        rootHostScope.cancel()
        Log.v(iTag, "start() RootHost finished")
    }

    abstract suspend fun onInit()

    abstract suspend fun onExecute()

    @SuppressLint("PrivateApi,DiscouragedPrivateApi")
    private fun setAppName(name: String?) = try {
        log(iTag) { "Setting appName=$name" }
        val ddm = Class.forName("android.ddm.DdmHandleAppName")
        val m: Method = ddm.getDeclaredMethod("setAppName", String::class.java, Int::class.javaPrimitiveType)
        m.invoke(null, name, 0)
    } catch (e: Exception) {
        throw RuntimeException(e)
    }

    /**
     * Retrieve system context<br></br>
     * <br></br>
     * Stability: unlikely to change, this implementation works from 1.6 through 9.0<br></br>
     *
     * @return system context
     */
    val systemContext: Context by lazy {
        try {
            // a prepared Looper is required for the calls below to succeed
            if (Looper.getMainLooper() == null) {
                try {
                    Looper.prepareMainLooper()
                } catch (e: Exception) {
                    log(ERROR) { "Failed prepareMainLooper() for systemContext" }
                }
            }
            val cActivityThread = Class.forName("android.app.ActivityThread")
            val mSystemMain = cActivityThread.getMethod("systemMain")
            val mGetSystemContext = cActivityThread.getMethod("getSystemContext")
            val oActivityThread = mSystemMain.invoke(null)
            val oContext = mGetSystemContext.invoke(oActivityThread)
            log { "Grabbed context $oContext" }
            oContext as Context
        } catch (e: Exception) {
            log(ERROR) { "Failed to obtain system context: ${e.asLog()}" }
            throw RuntimeException("Unexpected exception in getSystemContext()")
        }
    }

    companion object {
        const val OPTIONS_KEY = "ROOT_HOST_OPTIONS"
    }
}