package eu.darken.sdmse.common.device

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import eu.darken.sdmse.common.BuildWrap
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject

/**
 * Configuration builder for mocking device properties in tests
 */
class DeviceConfigBuilder {
    var manufacturer: String = ""
    var brand: String? = null
    var display: String? = null
    var product: String? = null
    var versionIncremental: String = ""
    var sdkInt: Int = 33
    var installedPackages: Set<String> = emptySet()
    var isAndroidTV: Boolean = false
    var uiModeType: Int = Configuration.UI_MODE_TYPE_NORMAL
    var hasTelevisionFeature: Boolean = false
    var hasLeanbackFeature: Boolean = false
}

/**
 * DSL function to mock a device configuration for testing
 *
 * Example usage:
 * ```
 * mockDevice {
 *     manufacturer = "Xiaomi"
 *     versionIncremental = "V816.0.1.0.UMNMIXM"
 *     sdkInt = 34
 *     installedPackages = setOf("com.miui.securitycenter")
 * }
 * ```
 */
fun mockDevice(context: Context = mockk(relaxed = true), block: DeviceConfigBuilder.() -> Unit): Context {
    val config = DeviceConfigBuilder().apply(block)

    // Mock BuildWrap object
    mockkObject(BuildWrap)
    every { BuildWrap.MANUFACTOR } returns config.manufacturer
    every { BuildWrap.BRAND } returns config.brand
    every { BuildWrap.DISPLAY } returns config.display
    every { BuildWrap.PRODUCT } returns config.product

    every { BuildWrap.VERSION.SDK_INT } returns config.sdkInt
    every { BuildWrap.VERSION.INCREMENTAL } returns config.versionIncremental

    // Mock Context and PackageManager
    val packageManager = mockk<PackageManager>(relaxed = true)
    every { context.packageManager } returns packageManager

    // Mock package installation checks
    every { packageManager.getPackageInfo(any<String>(), any<Int>()) } answers {
        val pkg = firstArg<String>()
        if (pkg in config.installedPackages) {
            mockk(relaxed = true)
        } else {
            throw PackageManager.NameNotFoundException()
        }
    }

    // Mock Android TV detection
    val uiModeManager = mockk<UiModeManager>(relaxed = true)
    every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
    every { uiModeManager.currentModeType } returns config.uiModeType
    every { packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) } returns config.hasTelevisionFeature
    every { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns config.hasLeanbackFeature

    return context
}

/**
 * Helper function to create a device based on a real device fingerprint
 * Parses the fingerprint format: manufacturer/product/device:version/build/incremental:type/keys
 *
 * Example:
 * ```
 * deviceFromFingerprint("Xiaomi/corot_global/corot:15/AP3A.240617.008/OS2.0.6.0.VMLMIXM:user/release-keys")
 * ```
 */
fun deviceFromFingerprint(
    fingerprint: String,
    installedPackages: Set<String> = emptySet(),
    context: Context = mockk(relaxed = true),
): Context {
    // Parse fingerprint: manufacturer/product/device:version/build/incremental:type/keys
    // Example: Xiaomi/plato_id/plato:13/TP1A.220624.014/V14.0.1.0.TLQIDXM:user/release-keys
    val parts = fingerprint.split("/")
    val manufacturer = parts.getOrNull(0) ?: ""
    val product = parts.getOrNull(1)

    // Extract Android version from device:version part (parts[2])
    val deviceVersionPart = parts.getOrNull(2) ?: ""
    val androidVersion = deviceVersionPart.split(":").getOrNull(1)?.toIntOrNull() ?: 33

    // Map Android version to API level
    val sdkInt = when (androidVersion) {
        9 -> 28
        10 -> 29
        11 -> 30
        12 -> 31
        13 -> 33
        14 -> 34
        15 -> 35
        16 -> 36
        else -> androidVersion // Fallback for future versions
    }

    // Extract version incremental from incremental:type part (parts[4])
    val incrementalPart = parts.getOrNull(4) ?: ""
    val versionIncremental = incrementalPart.split(":").firstOrNull() ?: ""

    return mockDevice(context) {
        this.manufacturer = manufacturer
        this.product = product
        this.versionIncremental = versionIncremental
        this.sdkInt = sdkInt
        this.installedPackages = installedPackages
    }
}
