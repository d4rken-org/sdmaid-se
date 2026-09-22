package eu.darken.sdmse.common.storage

import android.os.Build
import android.util.Log

/** Goes into every clue so a divergence can be attributed to a concrete image, not just an API level. */
internal val deviceIdentity: String
    get() = "SDK_INT=${Build.VERSION.SDK_INT} fingerprint=${Build.FINGERPRINT}"

/** Every observation is logged on every run, so a green run keeps its evidence. */
internal fun clue(detail: String): String = "$deviceIdentity | $detail".also { Log.i("SDMSEPROBE", "observed: $it") }

/**
 * A level this lane never ran on must fail loudly. Silently skipping is the regression these tests exist to prevent.
 */
internal fun unpinnedSdk(subject: String): Nothing = throw AssertionError(
    "No pin covers $subject on $deviceIdentity. Observe that level and extend the ranges, do not skip it."
)

internal fun logDeviceIdentity() {
    Log.i("SDMSEPROBE", deviceIdentity)
}
