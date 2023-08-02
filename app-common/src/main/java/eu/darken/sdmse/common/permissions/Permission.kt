package eu.darken.sdmse.common.permissions

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.DeviceDetective
import kotlin.reflect.full.isSubclassOf


@Suppress("ClassName")
sealed class Permission(
    val permissionId: String
) {
    open fun isGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, permissionId) == PackageManager.PERMISSION_GRANTED
    }

    object POST_NOTIFICATIONS
        : Permission("android.permission.POST_NOTIFICATIONS"), RuntimePermission

    @SuppressLint("BatteryLife")
    object IGNORE_BATTERY_OPTIMIZATION
        : Permission("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"), Specialpermission {
        override fun isGranted(context: Context): Boolean {
            val pwm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pwm.isIgnoringBatteryOptimizations(BuildConfigWrap.APPLICATION_ID)
        }

        override fun createIntent(context: Context, deviceDetective: DeviceDetective): Intent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.fromParts("package", context.packageName, null)
        }

        override fun createIntentFallback(context: Context): Intent = Intent().apply {
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    object MANAGE_EXTERNAL_STORAGE
        : Permission("android.permission.MANAGE_EXTERNAL_STORAGE"), Specialpermission {
        override fun isGranted(context: Context): Boolean {
            return Environment.isExternalStorageManager()
        }

        override fun createIntent(context: Context, deviceDetective: DeviceDetective): Intent = Intent().apply {
            action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
            data = Uri.fromParts("package", context.packageName, null)
        }

        override fun createIntentFallback(context: Context): Intent = Intent().apply {
            action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
        }
    }

    object WRITE_EXTERNAL_STORAGE
        : Permission("android.permission.WRITE_EXTERNAL_STORAGE"), RuntimePermission

    object READ_EXTERNAL_STORAGE
        : Permission("android.permission.READ_EXTERNAL_STORAGE"), RuntimePermission

    object PACKAGE_USAGE_STATS
        : Permission("android.permission.PACKAGE_USAGE_STATS"), Specialpermission {
        override fun isGranted(context: Context): Boolean {
            val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                applicationInfo.uid,
                applicationInfo.packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }

        override fun createIntent(context: Context, deviceDetective: DeviceDetective): Intent {
            val defaultIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            return when {
                defaultIntent.resolveActivities(context).isNotEmpty() -> return defaultIntent
                deviceDetective.isAndroidTV() -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
                else -> Intent(Settings.ACTION_SETTINGS)
            }
        }
    }

    object WRITE_SECURE_SETTINGS
        : Permission("android.permission.WRITE_SECURE_SETTINGS")

    fun Intent.resolveActivities(context: Context): Collection<ResolveInfo> =
        context.packageManager.queryIntentActivities(
            this,
            PackageManager.MATCH_DEFAULT_ONLY
        )

    companion object {
        // Without lazy there is an NPE: https://youtrack.jetbrains.com/issue/KT-25957
        val values: List<Permission> by lazy {
            Permission::class.nestedClasses
                .filter { clazz -> clazz.isSubclassOf(Permission::class) }
                .map { clazz -> clazz.objectInstance }
                .filterIsInstance<Permission>()
        }

        fun fromId(rawId: String) = values.singleOrNull { it.permissionId == rawId }
    }
}

interface RuntimePermission

interface Specialpermission {
    fun createIntent(context: Context, deviceDetective: DeviceDetective): Intent
    fun createIntentFallback(context: Context): Intent? = null
}