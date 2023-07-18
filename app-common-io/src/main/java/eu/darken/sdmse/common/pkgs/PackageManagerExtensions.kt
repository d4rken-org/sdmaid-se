package eu.darken.sdmse.common.pkgs

import android.content.ComponentName
import android.content.pm.IPackageDataObserver
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.SharedLibraryInfo
import android.graphics.drawable.Drawable
import android.os.RemoteException
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.user.UserHandle2
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.jvmErasure

fun PackageManager.getPackageInfo2(
    pkgId: Pkg.Id,
    flags: Int = 0
): PackageInfo? = try {
    getPackageInfo(pkgId.name, flags)
} catch (_: PackageManager.NameNotFoundException) {
    null
}

fun PackageManager.getLabel2(
    pkgId: Pkg.Id,
): String? = getPackageInfo2(pkgId)
    ?.applicationInfo
    ?.let {
        if (it.labelRes != 0) it.loadLabel(this).toString()
        else it.nonLocalizedLabel?.toString()
    }

fun PackageManager.getIcon2(
    pkgId: Pkg.Id,
): Drawable? = getPackageInfo2(pkgId)
    ?.applicationInfo
    ?.let { if (it.icon != 0) it.loadIcon(this) else null }


fun PackageManager.getInstalledPackagesAsUser(
    flags: Int,
    userHandle: UserHandle2,
) = try {
    PackageManager::class.memberFunctions
        .filter { it.name == "getInstalledPackagesAsUser" }
        .first {
            val arg1 = it.parameters[1].type.jvmErasure
            val arg2 = it.parameters[2].type.jvmErasure
            Int::class.isSubclassOf(arg1) && Int::class.isSubclassOf(arg2)
        }
        .call(this, flags, userHandle.handleId) as List<PackageInfo>
} catch (e: Exception) {
    throw IOException("getInstalledPackagesAsUser($flags,$userHandle) failed", e)
}

// WORKAROUND
fun PackageManager.getSharedLibraries2(flags: Int): List<SharedLibraryInfo> = try {
    getSharedLibraries(flags)
} catch (e: Exception) {
    log("PackageManager", ERROR) { "Failed getSharedLibraries($flags)" }
    // https://github.com/d4rken/sdmaid-public/issues/3100
    if (hasApiLevel(29)) throw e else emptyList()
}

fun PackageManager.toggleSelfComponent(
    component: ComponentName,
    enabled: Boolean,
) {
    log { "toggleSelfComponent($component,$enabled)" }
    setComponentEnabledSetting(
        component,
        when {
            enabled -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        },
        PackageManager.DONT_KILL_APP
    )
}

suspend fun PackageManager.freeStorageAndNotify(
    desiredBytes: Long = Long.MAX_VALUE - 1000L,
    storageId: String? = null,
    timeout: Long = 30 * 1000L
): Boolean {
    val packageManager = this

    val freeStorageAndNotifyMethod = packageManager.javaClass.getMethod(
        "freeStorageAndNotify",
        String::class.java,
        Long::class.javaPrimitiveType,
        IPackageDataObserver::class.java
    )

    return withTimeout(timeout) {
        suspendCancellableCoroutine { continuation ->
            try {
                if (Bugs.isDryRun) {
                    continuation.resume(true)
                    return@suspendCancellableCoroutine
                }

                freeStorageAndNotifyMethod.invoke(
                    packageManager,
                    storageId,
                    desiredBytes,
                    object : IPackageDataObserver.Stub() {
                        @Throws(RemoteException::class)
                        override fun onRemoveCompleted(packageName: String?, succeeded: Boolean) {
                            log(VERBOSE) { "freeStorageAndNotify() $packageName -> $succeeded" }
                            continuation.resume(true)
                        }
                    }
                )
            } catch (e: Exception) {
                log(WARN) { "freeStorageAndNotify($desiredBytes,$storageId) failed: ${e.asLog()}" }
                continuation.resume(false)
            }
        }
    }
}

/**
 * Requires android.permission.DELETE_CACHE_FILES
 */
suspend fun PackageManager.deleteApplicationCacheFiles(
    packageName: String
): Boolean {
    val packageManager = this

    val deleteApplicationCacheFilesMethod = packageManager.javaClass.getMethod(
        "deleteApplicationCacheFiles",
        String::class.java,
        IPackageDataObserver::class.java
    )

    return withTimeout(20 * 1000L) {
        suspendCancellableCoroutine { continuation ->
            try {
                deleteApplicationCacheFilesMethod.invoke(
                    packageManager,
                    packageName,
                    object : IPackageDataObserver.Stub() {
                        @Throws(RemoteException::class)
                        override fun onRemoveCompleted(packageName: String?, succeeded: Boolean) {
                            log(VERBOSE) { "deleteApplicationCacheFiles() $packageName -> $succeeded" }
                            continuation.resume(succeeded)
                        }
                    }
                )
            } catch (e: Exception) {
                log(WARN) { "deleteApplicationCacheFiles($packageName) failed: ${e.asLog()}" }
                continuation.resume(false)
            }
        }
    }
}

/**
 * Requires android.permission.INTERNAL_DELETE_CACHE_FILES
 */
suspend fun PackageManager.deleteApplicationCacheFilesAsUser(
    packageName: String,
    userId: Int,
): Boolean {
    val packageManager = this

    val deleteApplicationCacheFilesAsUserMethod = packageManager.javaClass.getMethod(
        "deleteApplicationCacheFilesAsUser",
        String::class.java,
        Int::class.javaPrimitiveType,
        IPackageDataObserver::class.java
    )

    return withTimeout(20 * 1000L) {
        suspendCancellableCoroutine { continuation ->
            try {
                deleteApplicationCacheFilesAsUserMethod.invoke(
                    packageManager,
                    packageName,
                    userId,
                    object : IPackageDataObserver.Stub() {
                        @Throws(RemoteException::class)
                        override fun onRemoveCompleted(packageName: String?, succeeded: Boolean) {
                            log(VERBOSE) { "deleteApplicationCacheFilesAsUser() $packageName -> $succeeded" }
                            continuation.resume(succeeded)
                        }
                    }
                )
            } catch (e: Exception) {
                log(WARN) { "deleteApplicationCacheFilesAsUser($packageName,$userId) failed: ${e.asLog()}" }
                continuation.resume(false)
            }
        }
    }
}