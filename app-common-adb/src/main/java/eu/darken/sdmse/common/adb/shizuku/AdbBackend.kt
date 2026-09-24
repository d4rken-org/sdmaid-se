package eu.darken.sdmse.common.adb.shizuku

/**
 * Which privileged manager the ADB link talks to. Mirrors the SDK's own backend type so it doesn't
 * leak out of this module.
 *
 * [label] is a product name and deliberately not translated.
 */
enum class AdbBackend(val label: String) {
    PORTER("Porter"),
    SHIZUKU("Shizuku"),
}
