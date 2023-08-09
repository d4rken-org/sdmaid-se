package eu.darken.sdmse.common.coroutine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SuspendingLazy<T>(initializer: suspend () -> T) {
    private var _initializer: (suspend () -> T)? = initializer
    private var _lock: Mutex? = Mutex()
    private var cachedValue: T? = null

    @Suppress("UNCHECKED_CAST")
    suspend fun value(): T {
        val lock = _lock ?: return cachedValue as T
        return lock.withLock {
            val initializer = _initializer ?: return cachedValue as T
            initializer().also {
                cachedValue = it
                _initializer = null
                _lock = null
            }
        }
    }
}