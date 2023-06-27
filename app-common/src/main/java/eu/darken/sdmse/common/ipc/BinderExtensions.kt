package eu.darken.sdmse.common.ipc

import android.os.IBinder
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import kotlin.reflect.KClass

/**
 * Stability: stable, as changes to this pattern in AOSP would probably require all AIDL-using apps to be recompiled.
 *
 * @return T (proxy) instance or null
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> IBinder.getInterface(clazz: KClass<T>): T? = try {
    val fDescriptor = Class
        .forName(clazz.qualifiedName + "\$Stub")
        .getDeclaredField("DESCRIPTOR")
        .apply { isAccessible = true }

    val intf = queryLocalInterface(fDescriptor[this] as String)

    if (clazz.isInstance(intf)) {
        // local
        intf as T?
    } else {
        // remote
        val ctorProxy = Class
            .forName(clazz.qualifiedName + "\$Stub\$Proxy")
            .getDeclaredConstructor(IBinder::class.java)
            .apply { isAccessible = true }

        ctorProxy.newInstance(this) as T
    }
} catch (e: Exception) {
    log(ERROR) { "getInterfaceFromBinder() failed: ${e.asLog()}" }
    null
}