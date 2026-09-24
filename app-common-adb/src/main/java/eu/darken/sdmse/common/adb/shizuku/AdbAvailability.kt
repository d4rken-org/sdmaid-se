package eu.darken.sdmse.common.adb.shizuku

/** How far away the ADB manager is, see [PorterGateway.availability]. */
sealed interface AdbAvailability {

    /** No installed package declares a manager permission this app can use. */
    data object NotInstalled : AdbAvailability

    /**
     * [packageName] declares [backend]'s manager permission, renamed forks included. Null only when
     * [connected] and the manager was uninstalled while its server kept running.
     */
    data class Installed(
        val backend: AdbBackend,
        val packageName: String?,
        val connected: Boolean,
    ) : AdbAvailability

    /** A server answered, but it and this app share no protocol version. */
    data class Incompatible(
        val backend: AdbBackend,
        val packageName: String?,
        val serverTooOld: Boolean,
        val clientTooOld: Boolean,
    ) : AdbAvailability
}
