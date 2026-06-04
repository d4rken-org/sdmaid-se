package eu.darken.sdmse.common.root.service.internal

import android.content.Context
import android.os.Debug
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eu.darken.flowshell.core.cmd.FlowCmd
import eu.darken.flowshell.core.cmd.FlowCmdShell
import eu.darken.flowshell.core.cmd.execute
import eu.darken.flowshell.core.cmd.openSession
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Seams that let [RootHostLauncher.createConnection] be unit-tested without an actual rooted device.
 *
 * The launcher keeps ALL the orchestration (try/finally teardown ordering, mount-master, direct-vs-
 * relocation retry, the `connected` flag) — that's the logic under test. These three collaborators
 * are the only things that touch the OS/root boundary (a `su` shell, a broadcast receiver, and the
 * Android-only command building via [RootHostCmdBuilder]/[Debug]/Parcel), so tests can replace them
 * with fakes. The real implementations are exercised end-to-end on real devices.
 */
interface RootSession {
    suspend fun execute(cmd: FlowCmd): FlowCmd.Result

    /** Graceful close — writes `exit` and awaits the shell exiting. */
    suspend fun close()

    /** Forceful kill of the session. */
    suspend fun cancel()
}

interface RootSessionFactory {
    /** Opens a privileged ("su") session tied to [scope]. */
    suspend fun open(scope: CoroutineScope): RootSession
}

interface RootIpcReceiver {
    fun connect()

    /** Releases the receiver and tells the host we're leaving (sends `bye()`). */
    fun release()
}

interface RootIpcReceiverFactory {
    fun create(
        pairingCode: String,
        onConnect: (RootConnection) -> Unit,
        onDisconnect: (RootConnection) -> Unit,
    ): RootIpcReceiver
}

/** A per-connection command builder — created once so its init args (incl. [Debug] state) are frozen. */
interface RootLaunchCommand {
    fun build(withRelocation: Boolean): FlowCmd
}

interface RootLaunchCommandFactory {
    /**
     * Creates the per-connection [RootLaunchCommand]. Encapsulates [RootHostInitArgs] construction
     * (which touches [Debug] and the package name) plus [RootHostCmdBuilder] (which touches Parcel /
     * reflection), none of which run on a plain JVM — hence the seam. The args are captured ONCE here
     * so the direct-exec and relocation attempts use identical init args (matching pre-seam behaviour).
     */
    fun <Host : BaseRootHost> create(
        hostClass: KClass<Host>,
        pairingCode: String,
        options: RootHostOptions,
    ): RootLaunchCommand
}

internal class DefaultRootSessionFactory @Inject constructor() : RootSessionFactory {
    override suspend fun open(scope: CoroutineScope): RootSession {
        val session = FlowCmdShell("su").openSession(scope).first
        return object : RootSession {
            override suspend fun execute(cmd: FlowCmd): FlowCmd.Result = cmd.execute(session)
            override suspend fun close() = session.close()
            override suspend fun cancel() = session.cancel()
        }
    }
}

internal class DefaultRootIpcReceiverFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) : RootIpcReceiverFactory {
    override fun create(
        pairingCode: String,
        onConnect: (RootConnection) -> Unit,
        onDisconnect: (RootConnection) -> Unit,
    ): RootIpcReceiver {
        val receiver = object : RootConnectionReceiver(pairingCode) {
            override fun onConnect(connection: RootConnection) = onConnect(connection)
            override fun onDisconnect(connection: RootConnection) = onDisconnect(connection)
        }
        return object : RootIpcReceiver {
            override fun connect() = receiver.connect(context)
            override fun release() = receiver.release()
        }
    }
}

internal class DefaultRootLaunchCommandFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) : RootLaunchCommandFactory {
    override fun <Host : BaseRootHost> create(
        hostClass: KClass<Host>,
        pairingCode: String,
        options: RootHostOptions,
    ): RootLaunchCommand {
        // Captured once per connection — both launch attempts reuse these.
        val initArgs = RootHostInitArgs(
            pairingCode = pairingCode,
            packageName = context.packageName,
            waitForDebugger = options.isTrace && Debug.isDebuggerConnected(),
            isDebug = options.isDebug,
            isTrace = options.isTrace,
            isDryRun = options.isDryRun,
            recorderPath = options.recorderPath,
        )
        val cmdBuilder = RootHostCmdBuilder(context, hostClass)
        return object : RootLaunchCommand {
            override fun build(withRelocation: Boolean): FlowCmd =
                cmdBuilder.build(withRelocation = withRelocation, initialOptions = initArgs)
        }
    }
}

@InstallIn(SingletonComponent::class)
@Module
internal abstract class RootHostLauncherModule {
    @Binds abstract fun sessionFactory(impl: DefaultRootSessionFactory): RootSessionFactory

    @Binds abstract fun receiverFactory(impl: DefaultRootIpcReceiverFactory): RootIpcReceiverFactory

    @Binds abstract fun commandFactory(impl: DefaultRootLaunchCommandFactory): RootLaunchCommandFactory
}
