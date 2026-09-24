package eu.darken.sdmse.common.adb.shizuku

import android.os.IBinder
import eu.darken.porter.sdk.PorterConnection
import eu.darken.porter.sdk.UserServiceArgs
import eu.darken.porter.sdk.isGranted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * One connection to the ADB manager's server. A new instance per attach, so a restarted server is a
 * different link.
 *
 * Suspending calls are main-safe and cancel at once. Failures throw the SDK's unchecked exceptions.
 */
interface AdbLink {
    val backend: AdbBackend

    /** The uid the server runs as, 2000 for shell. Known from attach, reading it asks nothing. */
    val uid: Int

    /** Whether the server lets this app through, as it last reported. */
    val permission: Flow<Boolean>

    suspend fun checkPermission(): Boolean

    /** Suspends until the user answered the manager's prompt. Throws if the link is lost first. */
    suspend fun requestPermission(): Boolean

    /**
     * Binds the user service while collected and emits its binder once connected. Completes when the
     * service dies or the link is lost. Cancelling drops the binding but does not stop the process.
     */
    fun userService(args: UserServiceArgs): Flow<IBinder>

    /** Asks the user service to shut down via its destroy transaction. */
    suspend fun stopUserService(args: UserServiceArgs)
}

internal data class PorterAdbLink(private val connection: PorterConnection) : AdbLink {

    override val backend: AdbBackend = connection.backend.toAdbBackend()

    override val uid: Int
        get() = connection.uid

    override val permission: Flow<Boolean> = connection.permission
        .map { it.isGranted }
        .distinctUntilChanged()

    override suspend fun checkPermission(): Boolean = connection.checkPermission().isGranted

    override suspend fun requestPermission(): Boolean = connection.requestPermission().isGranted

    override fun userService(args: UserServiceArgs): Flow<IBinder> = connection.userService(args)

    override suspend fun stopUserService(args: UserServiceArgs) = connection.stopUserService(args)
}
