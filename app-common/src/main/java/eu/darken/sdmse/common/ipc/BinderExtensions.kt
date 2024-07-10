package eu.darken.sdmse.common.ipc

import android.os.IBinder
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import kotlin.reflect.KClass

/**
 * Stability: stable, as changes to this pattern in AOSP would probably require all AIDL-using apps to be recompiled.
 *
 * @return T (proxy) instance or null
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> IBinder.getInterface(clazz: KClass<T>): T? {
    // PROGUARD RULE REQUIRED: DESCRIPTOR field is otherwise removed
    // e.g. eu.darken.sdmse.common.root.service.RootServiceConnection.DESCRIPTOR
    val fDescriptor = Class
        .forName(clazz.qualifiedName + "\$Stub")
        .getField("DESCRIPTOR")
        .apply { isAccessible = true }

    val intf = queryLocalInterface(fDescriptor[this] as String)
    log(VERBOSE) { "Queried interface is $intf" }

    if (clazz.isInstance(intf)) {
        log(VERBOSE) { "Using local instance" }
        return intf as T?
    }

    log(VERBOSE) { "Creating remote instance" }
    val className = clazz.qualifiedName + "\$Stub\$Proxy"

    // PROGUARD RULE REQUIRED: `Proxy` constructor is otherwise removed
    // e.g. eu.darken.sdmse.common.shizuku.ShizukuServiceConnection$Stub$Proxy.<init> [interface android.os.IBinder]
    log(VERBOSE) { "Creating class $className" }
    val ctorProxy = Class
        .forName(className)
        .getConstructor(IBinder::class.java)
        .apply { isAccessible = true }

    return ctorProxy.newInstance(this) as T

}