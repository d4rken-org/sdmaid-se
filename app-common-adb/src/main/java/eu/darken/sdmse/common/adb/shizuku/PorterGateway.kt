package eu.darken.sdmse.common.adb.shizuku

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eu.darken.porter.sdk.Porter
import eu.darken.porter.sdk.PorterAvailability
import eu.darken.porter.sdk.PorterBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The only way into the Porter SDK, so everything above it can be tested without it. */
interface PorterGateway {

    /**
     * The current link, null before a server delivered a binder and after it died. A restarting
     * server usually publishes null before its new link.
     */
    val link: Flow<AdbLink?>

    /** Pings the server, so it takes as long as a wedged server does. Callers bound it. */
    suspend fun availability(): AdbAvailability
}

@Singleton
internal class DefaultPorterGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : PorterGateway {

    override val link: Flow<AdbLink?> = Porter.connection.map { connection -> connection?.let { PorterAdbLink(it) } }

    override suspend fun availability(): AdbAvailability = Porter.availability(context).toAdbAvailability()
}

internal fun PorterBackend.toAdbBackend(): AdbBackend = when (this) {
    PorterBackend.PORTER -> AdbBackend.PORTER
    PorterBackend.SHIZUKU -> AdbBackend.SHIZUKU
}

internal fun PorterAvailability.toAdbAvailability(): AdbAvailability = when (this) {
    PorterAvailability.NotInstalled -> AdbAvailability.NotInstalled
    is PorterAvailability.InstalledNotConnected -> AdbAvailability.Installed(
        backend = backend.toAdbBackend(),
        packageName = packageName,
        connected = false,
    )
    // A package the SDK does not know as the manager, e.g. a renamed fork: still the manager to us.
    is PorterAvailability.InstalledUnrecognized -> AdbAvailability.Installed(
        backend = backend.toAdbBackend(),
        packageName = packageName,
        connected = false,
    )
    is PorterAvailability.Connected -> AdbAvailability.Installed(
        backend = backend.toAdbBackend(),
        packageName = packageName,
        connected = true,
    )
    is PorterAvailability.Incompatible -> AdbAvailability.Incompatible(
        backend = incompatibility.backend.toAdbBackend(),
        packageName = packageName,
        serverTooOld = incompatibility.serverTooOld,
        clientTooOld = incompatibility.clientTooOld,
    )
}

@InstallIn(SingletonComponent::class)
@Module
internal abstract class PorterGatewayModule {
    @Binds abstract fun porterGateway(impl: DefaultPorterGateway): PorterGateway
}
