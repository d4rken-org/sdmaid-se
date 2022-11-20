package eu.darken.sdmse.common.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Need this to improve testing
// Can currently only replace the main-thread dispatcher.
// https://github.com/Kotlin/kotlinx.coroutines/issues/1365
@Suppress("PropertyName", "VariableNaming")
interface DispatcherProvider {
    val Default: CoroutineDispatcher
        get() = Dispatchers.Default
    val Main: CoroutineDispatcher
        get() = Dispatchers.Main
    val MainImmediate: CoroutineDispatcher
        get() = Dispatchers.Main.immediate
    val Unconfined: CoroutineDispatcher
        get() = Dispatchers.Unconfined
    val IO: CoroutineDispatcher
        get() = Dispatchers.IO
}
