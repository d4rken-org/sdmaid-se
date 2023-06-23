package eu.darken.sdmse.common.shizuku.service.internal

import android.annotation.SuppressLint
import android.content.Context
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.*
import kotlin.system.exitProcess

@SuppressLint("PrivateApi")
abstract class BaseShizukuHost(
    private val iTag: String,
    val context: Context,
) : ShizukuConnection.Stub() {

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