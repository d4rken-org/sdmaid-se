package eu.darken.sdmse.common.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import eu.darken.sdmse.common.BuildConfigWrap

enum class Permission(
    val permissionId: String,
    val isGranted: (Context) -> Boolean = {
        ContextCompat.checkSelfPermission(it, permissionId) == PackageManager.PERMISSION_GRANTED
    },
) {
    ACCESS_COARSE_LOCATION(
        permissionId = "android.permission.ACCESS_COARSE_LOCATION",
    ),
    ACCESS_FINE_LOCATION(
        permissionId = "android.permission.ACCESS_FINE_LOCATION",
    ),
    IGNORE_BATTERY_OPTIMIZATION(
        permissionId = "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        isGranted = {
            val pwm = it.getSystemService(Context.POWER_SERVICE) as PowerManager
            pwm.isIgnoringBatteryOptimizations(BuildConfigWrap.APPLICATION_ID)
        },
    ),
    POST_NOTIFICATIONS(
        permissionId = "android.permission.POST_NOTIFICATIONS",
    ),
}
