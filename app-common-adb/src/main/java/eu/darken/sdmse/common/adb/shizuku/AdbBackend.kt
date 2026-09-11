package eu.darken.sdmse.common.adb.shizuku

/**
 * Which privileged manager the ADB link talks to.
 *
 * Selected once per process by the client SDK and latched from then on, see
 * [ShizukuWrapper.activeBackend]. Mirrors the SDK's own backend type so it doesn't leak out of this
 * module.
 *
 * [label] is a product name and deliberately not translated.
 */
enum class AdbBackend(val label: String) {
    PORTER("Porter"),
    SHIZUKU("Shizuku"),
    ;

    /** The family this process can NOT talk to, i.e. what an inactive manager belongs to. */
    val other: AdbBackend
        get() = if (this == PORTER) SHIZUKU else PORTER
}
