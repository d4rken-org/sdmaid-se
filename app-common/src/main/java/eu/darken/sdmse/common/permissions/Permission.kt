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
    POST_NOTIFICATIONS(
        permissionId = "android.permission.POST_NOTIFICATIONS",
    ),
    IGNORE_BATTERY_OPTIMIZATION(
        permissionId = "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        isGranted = {
            val pwm = it.getSystemService(Context.POWER_SERVICE) as PowerManager
            pwm.isIgnoringBatteryOptimizations(BuildConfigWrap.APPLICATION_ID)
        },
    ),
    MANAGE_EXTERNAL_STORAGE(
        permissionId = "android.permission.MANAGE_EXTERNAL_STORAGE",
    ),
    WRITE_EXTERNAL_STORAGE(
        permissionId = "android.permission.WRITE_EXTERNAL_STORAGE",
    ),
    READ_EXTERNAL_STORAGE(
        permissionId = "android.permission.READ_EXTERNAL_STORAGE",
    ),
}
