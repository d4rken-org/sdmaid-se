package eu.darken.sdmse.common.root.service.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.IBinder.DeathRecipient
import android.os.RemoteException
import androidx.annotation.Keep
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import java.lang.ref.WeakReference

/**
 * Binder-based IPC receiver for the non-root process<br></br>
 * <br></br>
 * This class handles receiving the (wrapped) Binder interface, casting it to your own interface,
 * and handling connection state.
 */
//@SuppressWarnings({"unused", "WeakerAccess", "Convert2Diamond", "TryWithIdenticalCatches"})
@Keep
abstract class RootConnectionReceiver constructor(private val pairingCode: String) {

    private val handlerThread: HandlerThread by lazy {
        HandlerThread("javaroot:RootConnectionReceiver#$pairingCode")
    }
    private val handler: Handler by lazy {
        handlerThread.start()
        Handler(handlerThread.looper)
    }

    private val self: IBinder = Binder()
    private val binderSync = Object()
    private val eventSync = Object()

    private var contextRef: WeakReference<Context>? = null

    @Volatile private var binder: IBinder? = null
    @Volatile private var internalIpc: RootConnection? = null
    @Volatile private var inEvent = false
    @Volatile private var disconnectAfterEvent = false

    private val filter = IntentFilter(BROADCAST_ACTION)
    private val deathRecipient = DeathRecipient {
        synchronized(binderSync) {
            clearBinder()
            binderSync.notifyAll()
        }
    }

    /**
     * Actual BroadcastReceiver that handles receiving the RootConnection interface, sets up
     * an on-death callback, and says hello to the other side.
     */
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == null || intent.action != BROADCAST_ACTION) {
                log(TAG, WARN) { "Received unexpected intent: $intent" }
                return
            }

            val bundle = intent.getBundleExtra(BROADCAST_EXTRA)
            if (bundle == null) {
                log(TAG, WARN) { "Intent is missing a bundle" }
                return
            }

            val code = bundle.getString(BROADCAST_CODE)
            if (code != this@RootConnectionReceiver.pairingCode) {
                log(TAG, ERROR) { "Received invalid code, $code instead of ${this@RootConnectionReceiver.pairingCode}" }
                return
            }

            val received: IBinder = bundle.getBinder(BROADCAST_BINDER)
                ?: throw IllegalArgumentException("Intent is missing IBinder")

            try {
                received.linkToDeath(deathRecipient, 0)
            } catch (e: RemoteException) {
                throw e
            }

            synchronized(binderSync) {
                binder = received
                internalIpc = RootConnection.Stub.asInterface(binder).also {
                    log(TAG) { "Saved internalIpc=$it" }
                    try {
                        log(TAG) { "hello($self)" }
                        // we send over our own Binder that the other end can linkToDeath with
                        it.hello(self)

                        // schedule a call to doOnConnectRunnable so we stop blocking the receiver
                        handler.post {
                            synchronized(binderSync) { doOnConnect() }
                        }
                    } catch (e: RemoteException) {
                        log(TAG, ERROR) { "hello() failed: $it <-> $self" }
                    }
                }

                binderSync.notifyAll()
            }
        }
    }

    /**
     * Note that if this constructor is called in the class definition of a context (such as an
     * Activity), the context passed will not be a proper context, and you will need to call
     * [.setContext] in something like onCreate or the receiver will not function.
     */
    fun connect(context: Context) {
        if (context is ContextWrapper && context.baseContext == null) {
            // Constructed in activity class definition
            throw IllegalStateException("Invalid context")
        }

        this.contextRef = WeakReference(context)
        context.registerReceiver(receiver, filter, null, handler)
    }

    /**
     * Callback for when the IPC interface becomes available.<br></br>
     * <br></br>
     * This callback is always called from a background thread, it is safe to perform blocking
     * operations here.<br></br>
     * <br></br>
     * If another thread calls [.release] or [.disconnect], the connection is not
     * actually aborted until this callback returns. You can check for this state with
     * [.isDisconnectScheduled].<br></br>
     * <br></br>
     * This connection may still be severed at any time due to the process on the other end
     * dieing, any calls on the IPC interface will then throw a RemoteException.<br></br>
     * <br></br>
     * Do not store a reference to ipc, but use the [.getIPC] method to retrieve it when
     * you need it outside of this callback.
     *
     * @param ipc The Binder interface you declared in an aidl and passed to RootIPC on the root side
     */
    abstract fun onConnect(connection: RootConnection)

    /**
     * Callback for when the IPC interface is going (or has gone) away.<br></br>
     * <br></br>
     * The ipc parameter is there for reference, but it may not be safe to use. Avoid doing so.<br></br>
     *
     * @param ipc The Binder interface you declared in an aidl and passed to RootIPC on the root side
     */
    abstract fun onDisconnect(connection: RootConnection)

    private fun doOnConnect() {
        // must be called inside synchronized(binderSync)
        if (binder != null && internalIpc != null) {
            synchronized(eventSync) {
                disconnectAfterEvent = false
                inEvent = true
            }
            onConnect(internalIpc!!)
            synchronized(eventSync) {
                inEvent = false
                if (disconnectAfterEvent) {
                    disconnect()
                }
            }
        }
    }

    private fun doOnDisconnect() {
        // must be called inside synchronized(binderSync)
        if (binder != null && internalIpc != null) {
            // we don't need to set inEvent here, only applicable to onConnect()
            onDisconnect(internalIpc!!)
        }
    }

    private fun clearBinder() {
        // must be called inside synchronized(binderSync)
        doOnDisconnect()
        if (binder != null) {
            try {
                binder!!.unlinkToDeath(deathRecipient, 0)
            } catch (e: Exception) {
                // no action required
            }
        }
        binder = null
        internalIpc = null
    }

    private fun isInEvent(): Boolean {
        synchronized(eventSync) { return inEvent }
    }

    /**
     * Retrieve connection status<br></br>
     * <br></br>
     * Note that this may return false if a disconnect is schedule but we are actually still
     * connected.
     *
     * @return Connection available
     */
    val isConnected: Boolean
        get() = iPC != null

    /**
     * @return If a disconnect is scheduled
     */
    private val isDisconnectScheduled: Boolean
        get() {
            synchronized(eventSync) {
                if (disconnectAfterEvent) {
                    return true
                }
            }
            return false
        }

    /**
     * If connected, disconnect or schedule a disconnect
     */
    fun disconnect() {
        synchronized(eventSync) {
            if (inEvent) {
                disconnectAfterEvent = true
                return
            }
        }
        synchronized(binderSync) {
            if (internalIpc != null) {
                try {
                    internalIpc!!.bye(self)
                } catch (e: RemoteException) {
                    // peer left without saying bye, rude!
                }
            }
            clearBinder()
        }
    }

    /**
     * Release all resources and (schedule a) disconnect if connected.<br></br>
     * <br></br>
     * Should be called when the context goes away, such as in on onDestroy()
     */
    fun release() {
        disconnect()
        contextRef?.get()?.unregisterReceiver(receiver)
        handlerThread.quitSafely()
    }// otherwise this call would deadlock when called from onConnect()
    // we know internalIpc is valid in this case
    /**
     * Retrieve IPC interface immediately
     *
     * @return Your IPC interface if connected, null otherwise
     */
    val iPC: RootConnection?
        get() {
            if (isDisconnectScheduled) return null
            if (isInEvent()) {
                // otherwise this call would deadlock when called from onConnect()
                // we know internalIpc is valid in this case
                return internalIpc
            }
            synchronized(binderSync) {
                if (binder != null) {
                    if (!binder!!.isBinderAlive) {
                        clearBinder()
                    }
                }
                if (binder != null && internalIpc != null) {
                    return internalIpc
                }
            }
            return null
        }

    /**
     * Retrieve IPC interface, waiting for it in case it isn't available
     *
     * @param timeout_ms Time to wait for a connection (if &gt; 0)
     * @return Your IPC interface if connected, null otherwise
     */
    fun getIPC(timeout_ms: Int): RootConnection? {
        if (isDisconnectScheduled) return null
        if (isInEvent()) {
            // otherwise this call would deadlock when called from onConnect()
            // we know internalIpc is valid in this case
            return internalIpc
        }
        if (timeout_ms <= 0) return iPC
        synchronized(binderSync) {
            if (binder == null) {
                try {
                    binderSync.wait(timeout_ms.toLong())
                } catch (e: InterruptedException) {
                    // no action required
                }
            }
        }
        return iPC
    }

    companion object {
        private val TAG = logTag("Root", "Connection", "Receiver")

        const val BROADCAST_ACTION = "eu.darken.sdmse.common.root.service.internal.RootIPCReceiver.BROADCAST"
        const val BROADCAST_EXTRA = "eu.darken.sdmse.common.root.service.internal.RootIPCReceiver.BROADCAST.EXTRA"
        const val BROADCAST_BINDER = "binder"
        const val BROADCAST_CODE = "code"
    }
}