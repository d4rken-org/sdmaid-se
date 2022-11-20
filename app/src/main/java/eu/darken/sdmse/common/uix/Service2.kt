package eu.darken.sdmse.common.uix

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag

abstract class Service2 : Service() {
    private val tag: String =
        logTag("Service", this.javaClass.simpleName + "(" + Integer.toHexString(this.hashCode()) + ")")

    override fun onCreate() {
        log(tag) { "onCreate()" }
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log(tag) { "onStartCommand(intent=$intent, flags=$flags startId=$startId)" }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        log(tag) { "onDestroy()" }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        log(tag) { "onConfigurationChanged(newConfig=$newConfig)" }
        super.onConfigurationChanged(newConfig)
    }

    override fun onLowMemory() {
        log(tag) { "onLowMemory()" }
        super.onLowMemory()
    }

    override fun onUnbind(intent: Intent): Boolean {
        log(tag) { "onUnbind(intent=$intent)" }
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent) {
        log(tag) { "onRebind(intent=$intent)" }
        super.onRebind(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        log(tag) { "onTaskRemoved(rootIntent=$rootIntent)" }
        super.onTaskRemoved(rootIntent)
    }
}
