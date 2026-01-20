package eu.darken.sdmse.common.permissions

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
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
        private val TAG = logTag("Permission", "UsageStats")


        @Suppress("DEPRECATION")
        override fun isGranted(context: Context): Boolean {
            val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                applicationInfo.uid,
                applicationInfo.packageName
            )
            log(TAG, VERBOSE) { "checkOpNoThrow(OPSTR_GET_USAGE_STATS)=$mode" }

            return when (mode) {
                AppOpsManager.MODE_ALLOWED -> {
                    log(TAG, VERBOSE) { "MODE_ALLOWED: Permission explicitly allowed via AppOps" }
                    true
                }

                AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> {
                    log(TAG, VERBOSE) { "MODE_IGNORED|MODE_ERRORED: Permission explicitly denied via AppOps" }
                    false
                }

                AppOpsManager.MODE_DEFAULT -> {
                    log(TAG, VERBOSE) { "MODE_DEFAULT: Has not been changed or errorneous report." }
                    val result = ContextCompat.checkSelfPermission(context, permissionId)
                    log(TAG, VERBOSE) { "checkSelfPermission(PACKAGE_USAGE_STATS)=$result" }
                    result == PackageManager.PERMISSION_GRANTED
                }

                else -> {
                    log(TAG, WARN) { "Unknown mode $mode, assuming permission denied" }
                    false
                }
            }
        }

        override fun createIntent(context: Context, deviceDetective: DeviceDetective): Intent {
            val defaultIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            return when {
                defaultIntent.resolveActivities(context).isNotEmpty() -> return defaultIntent
                deviceDetective.getROMType() == RomType.ANDROID_TV -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
                else -> Intent(Settings.ACTION_SETTINGS)
            }
        }
    }

    data object WRITE_SECURE_SETTINGS
        : Permission("android.permission.WRITE_SECURE_SETTINGS")

    data object QUERY_ALL_PACKAGES
        : Permission("android.permission.QUERY_ALL_PACKAGES")

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