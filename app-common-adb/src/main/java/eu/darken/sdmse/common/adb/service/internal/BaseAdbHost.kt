package eu.darken.sdmse.common.adb.service.internal

import android.annotation.SuppressLint
import android.content.Context
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

@SuppressLint("PrivateApi")
abstract class BaseAdbHost(
    private val iTag: String,
    val context: Context,
) : AdbConnection.Stub() {

    val hostScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun destroy() {
        log(iTag) { "destroy()" }
        runBlocking { onDestroy() }
        exitProcess(0)
    }

    override fun exit() {
        log(iTag) { "exit()" }
        destroy()
    }

    abstract suspend fun onDestroy()
}