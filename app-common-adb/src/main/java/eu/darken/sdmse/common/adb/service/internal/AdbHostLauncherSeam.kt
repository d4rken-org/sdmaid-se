package eu.darken.sdmse.common.adb.service.internal

import android.content.ComponentName
import android.os.IBinder
import android.os.IInterface
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.porter.sdk.UserServiceArgs
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.adb.AdbException
import eu.darken.sdmse.common.adb.service.AdbHostOptions
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.ipc.getInterface
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Seam that lets [AdbHostLauncher.createConnection] be unit-tested on a plain JVM: building the
 * [UserServiceArgs] needs [ComponentName] and [BuildConfigWrap], the handshake needs a real binder.
 */
interface AdbHostLauncherSeam {

    fun <Host : AdbConnection> userServiceArgs(hostClass: KClass<Host>, options: AdbHostOptions): UserServiceArgs

    /**
     * Post-connect handshake: validate the binder, wrap it, push the initial host options and resolve
     * our user interface. Every failure is thrown to the caller (the launcher decides what to do with
     * it), nothing is swallowed here. All of this does binder transactions.
     */
    fun <Service : IInterface, Host : AdbConnection> handshake(
        binder: IBinder,
        serviceClass: KClass<Service>,
        options: AdbHostOptions,
    ): Pair<Service, Host>
}

internal class DefaultAdbHostLauncherSeam @Inject constructor() : AdbHostLauncherSeam {

    override fun <Host : AdbConnection> userServiceArgs(
        hostClass: KClass<Host>,
        options: AdbHostOptions,
    ): UserServiceArgs = UserServiceArgs(
        componentName = ComponentName(BuildConfigWrap.APPLICATION_ID, hostClass.qualifiedName!!),
        processNameSuffix = logTag("ADB"),
        version = BuildConfigWrap.VERSION_CODE.toInt(),
        debuggable = options.isDebug,
        daemon = false,
    )

    @Suppress("UNCHECKED_CAST")
    override fun <Service : IInterface, Host : AdbConnection> handshake(
        binder: IBinder,
        serviceClass: KClass<Service>,
        options: AdbHostOptions,
    ): Pair<Service, Host> {
        if (!binder.pingBinder()) throw AdbException("Invalid binder (ping failed)")

        val baseConnection = AdbConnection.Stub.asInterface(binder)
            ?: throw AdbException("Failed to get base connection")

        // Initial options, the user service has no init arguments through which these can be supplied earlier
        baseConnection.updateHostOptions(options)

        val userConnection = baseConnection.userConnection.getInterface(serviceClass) as Service

        return userConnection to (baseConnection as Host)
    }
}

@InstallIn(SingletonComponent::class)
@Module
internal abstract class AdbHostLauncherModule {
    @Binds abstract fun adbHostLauncherSeam(impl: DefaultAdbHostLauncherSeam): AdbHostLauncherSeam
}
