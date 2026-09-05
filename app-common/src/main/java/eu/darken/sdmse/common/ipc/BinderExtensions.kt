package eu.darken.sdmse.common.ipc

import android.os.IBinder
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlin.reflect.KClass

/**
 * Stability: stable, as changes to this pattern in AOSP would probably require all AIDL-using apps to be recompiled.
 *
 * @return T (proxy) instance or null
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> IBinder.getInterface(clazz: KClass<T>): T? {
    // PROGUARD RULE REQUIRED: the `Stub` class and its DESCRIPTOR field are otherwise removed/renamed
    // see app-common-io/consumer-rules.pro
    val fDescriptor = Class
        .forName(clazz.qualifiedName + "\$Stub")
        .getField("DESCRIPTOR")
        .apply { isAccessible = true }

    val intf = queryLocalInterface(fDescriptor[this] as String)
    log(TAG, VERBOSE) { "Queried interface is $intf" }

    if (clazz.isInstance(intf)) {
        log(TAG, VERBOSE) { "Using local instance" }
        return intf as T?
    }

    log(TAG, VERBOSE) { "Creating remote instance" }
    val className = clazz.qualifiedName + "\$Stub\$Proxy"

    // PROGUARD RULE REQUIRED: the `Proxy` class and its IBinder constructor are otherwise removed/renamed
    // see app-common-io/consumer-rules.pro
    log(TAG, VERBOSE) { "Creating class $className" }
    val ctorProxy = Class
        .forName(className)
        .getDeclaredConstructor(IBinder::class.java)
        .apply { isAccessible = true }

    return ctorProxy.newInstance(this) as T
}


private val TAG = logTag("IPC", "Binder", "Extensions")
