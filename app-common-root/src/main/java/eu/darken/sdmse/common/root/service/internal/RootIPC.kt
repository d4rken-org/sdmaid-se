package eu.darken.sdmse.common.root.service.internal

import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.IBinder.DeathRecipient
import android.os.RemoteException
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.flowshell.core.cmd.FlowCmd
import eu.darken.flowshell.core.cmd.execute
import eu.darken.flowshell.core.process.FlowProcess
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.*
import java.util.concurrent.TimeoutException

/**
 * Based on https://github.com/Chainfire/librootjava
 * Binder-based IPC server for the root process<br></br>
 * <br></br>
 * This class wraps the supplied Binder interface in its own helper (primarily to keep track of
 * the non-root processes' state), and broadcasts the wrapper to the non-root process.

 * @param packageName       Package name of process to send Binder to. Use BuildConfig.APPLICATION_ID (double check you're importing the correct BuildConfig!) for convenience
 * @param userBinder        Binder object to wrap and send out
 * @param pairingCode       User-value, should be unique per Binder
 * @param timeout           How long to wait for the other process to initiate the connection, 0 to wait forever
 * @throws TimeoutException If the connection times out

 * @see RootConnectionReceiver
 */
class RootIPC @AssistedInject constructor(
    @Assisted("initArgs") private val initArgs: RootHostInitArgs,
    @Assisted private val userBinder: IBinder,
    @Assisted private val timeout: Long,
    @Assisted private val blocking: Boolean,
    private val reflectionBroadcast: ReflectionBroadcast,
) {

    private val helloWaiter = Object()
    private val byeWaiter = Object()

    data class Connection(val binder: IBinder, val deathRecipient: DeathRecipient)

    val connections = mutableListOf<Connection>()
    val hostOptions = MutableStateFlow(RootHostOptions.fromInitArgs(initArgs))

    @Volatile private var connectionSeen = false

    /**
     * Our own wrapper around the supplied Binder interface, which allows us to keep track of
     * non-root process' state and connection state.
     */
    private val internalBinder: IBinder = object : RootConnection.Stub() {
        override fun hello(_self: IBinder) {
            log(TAG) { "hello(self=$_self)" }
            // incoming connection from the non-root process
            var self: IBinder? = _self

            // receive notifications when that process dies
            val deathRecipient: DeathRecipient = object : DeathRecipient {
                override fun binderDied() {
                    getConnection(this)?.let { bye(it.binder) }
                }
            }
            try {
                log(TAG) { "linkToDeath(deathRecipient=$deathRecipient)" }
                self!!.linkToDeath(deathRecipient, 0)
            } catch (e: RemoteException) {
                log(TAG) { "linkToDeath() failed, dead? ${e.asLog()}" }
                // it's already dead!
                self = null
            }

            // if still alive, record the connection
            self?.let {
                log(TAG) { "Adding new connection..." }
                synchronized(connections) {
                    connections.add(Connection(it, deathRecipient))
                    connectionSeen = true
                }
                log(TAG) { "Notifying hello waiters" }
                synchronized(helloWaiter) { helloWaiter.notifyAll() }
            }
        }

        // this is the originally supplied Binder interface
        override fun getUserConnection(): IBinder = this@RootIPC.userBinder.also {
            log(TAG, VERBOSE) { "getUserConnection($it)" }
        }

        override fun bye(self: IBinder) {
            log(TAG) { "self(self=$self)" }
            // The non-root process is either informing us it is going away, or it already died
            synchronized(connections) {
                getConnection(self)?.let { conn ->
                    try {
                        conn.binder.unlinkToDeath(conn.deathRecipient, 0)
                    } catch (e: Exception) {
                        log(TAG) { "unlinkToDeath() failed: ${e.asLog()}" }
                    }
                    connections.remove(conn)
                }
            }
            synchronized(byeWaiter) { byeWaiter.notifyAll() }
        }

        override fun updateHostOptions(options: RootHostOptions) {
            log(TAG) { "updateHostOptions(): $options" }
            hostOptions.value = options
        }
    }

    init {
        log(TAG) { "init(): $initArgs, $userBinder, $timeout, $reflectionBroadcast" }
        require(timeout >= 0L) { "Timeout can't be negative: $timeout" }
    }

    suspend fun broadcastAndWait() {
        log(TAG) { "broadcast()" }

        broadcastIPC()

        if (timeout > 0) {
            synchronized(helloWaiter) {
                if (!haveClientsConnected()) {
                    try {
                        log(TAG) { "Waiting for clients (on timeout)" }
                        helloWaiter.wait(timeout)
                    } catch (e: InterruptedException) {
                        // expected, do nothing
                    }
                }
                if (!haveClientsConnected()) {
                    throw TimeoutException("Timeout waiting for IPC connection")
                }
            }
        }

        if (!blocking) return

        // this will loop until all connections have said goodbye or their processes have died
        synchronized(byeWaiter) {
            while (!haveAllClientsDisconnected()) {
                try {
                    log(TAG) { "Waiting for clients to disconnect (no timeout)" }
                    byeWaiter.wait()
                } catch (e: InterruptedException) {
                    log(TAG) { "Finished due to interrupt." }
                    return@synchronized
                }
            }

            log(TAG) { "Finished as all clients have disconnected." }
        }
    }

    fun haveClientsConnected(): Boolean = synchronized(connections) { connectionSeen }

    fun haveAllClientsDisconnected(): Boolean = synchronized(connections) { connectionSeen && connectionCount == 0 }

    /**
     * Wrap the binder in an intent and broadcast it to packageName
     * Uses the reflected sendBroadcast method that doesn't require us to have a context
     * You may call this manually to re-broadcast the interface
     */
    private suspend fun broadcastIPC() {
        val bundle = Bundle().apply {
            putBinder(RootConnectionReceiver.BROADCAST_BINDER, internalBinder)
            putString(RootConnectionReceiver.BROADCAST_CODE, initArgs.pairingCode)
        }

        val intent = Intent().apply {
            setPackage(initArgs.packageName)
            action = RootConnectionReceiver.BROADCAST_ACTION
            flags = Intent.FLAG_RECEIVER_FOREGROUND
            putExtra(RootConnectionReceiver.BROADCAST_EXTRA, bundle)
        }

        getUserIds().forEach {
            reflectionBroadcast.sendBroadcast(intent, it)
        }
    }


    val connectionCount: Int
        get() = synchronized(connections) {
            pruneConnections()
            connections.size
        }

    /**
     *This should never actually have any effect due to our DeathRecipients.
     */
    private fun pruneConnections() {
        synchronized(connections) {
            if (connections.size == 0) return
            connections.removeAll { con ->
                !con.binder.isBinderAlive.also {
                    log { "pruneConnections() $con: isBinderAlive=$it" }
                }
            }
            if (!connectionSeen && connections.size > 0) {
                connectionSeen = true
                synchronized(helloWaiter) { helloWaiter.notifyAll() }
            }
            if (connections.size == 0) {
                synchronized(byeWaiter) { byeWaiter.notifyAll() }
            }
        }
    }

    internal fun getConnection(binder: IBinder): Connection? = synchronized(connections) {
        pruneConnections()
        connections.singleOrNull { it.binder === binder }
    }

    internal fun getConnection(deathRecipient: DeathRecipient): Connection? = synchronized(connections) {
        pruneConnections()
        connections.singleOrNull { it.deathRecipient === deathRecipient }
    }

    private suspend fun getUserIds(): List<Int> {
        val result = FlowCmd("pm list users").execute()
        if (result.exitCode != FlowProcess.ExitCode.OK) {
            log(TAG, ERROR) { "Failed to get user handles via shell: ${result.merged}" }
            return listOf(0)
        }

        val parsedIds = result.output.mapNotNull {
            try {
                val match = USER_ID_REGEX.matchEntire(it) ?: return@mapNotNull null
                match.groupValues[1].toInt()
            } catch (e: Exception) {
                log(TAG) { "Failed to extract user handles from shell: ${e.asLog()}" }
                null
            }
        }

        log(TAG) { "getUserIds(): $parsedIds" }

        if (parsedIds.isEmpty()) {
            log(TAG, ERROR) { "Failed to parse user handles" }
            return listOf(0)
        }

        return parsedIds
    }

    companion object {
        private val USER_ID_REGEX = Regex("^\\s+UserInfo\\W(\\d+)\\W.+?\$")
        private val TAG = logTag("Root", "IPC")
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("initArgs") initArgs: RootHostInitArgs,
            userProvidedBinder: IBinder,
            timeout: Long = 30 * 1000,
            blocking: Boolean = true,
        ): RootIPC
    }
}