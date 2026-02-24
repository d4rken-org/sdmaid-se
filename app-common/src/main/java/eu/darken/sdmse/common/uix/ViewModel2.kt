package eu.darken.sdmse.common.uix

import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import eu.darken.sdmse.common.coroutine.DefaultDispatcherProvider
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.error.ErrorEventSource
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlin.coroutines.CoroutineContext


abstract class ViewModel2(
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : ViewModel1() {

    var launchErrorHandler: CoroutineExceptionHandler? = null

    private fun getVMContext(): CoroutineContext {
        val dispatcher = dispatcherProvider.Default
        return getErrorHandler()?.let { dispatcher + it } ?: dispatcher
    }

    val vmScope = viewModelScope + getVMContext()

    private fun getErrorHandler(): CoroutineExceptionHandler? {
        val handler = launchErrorHandler
        if (handler != null) return handler

        if (this is ErrorEventSource) {
            return CoroutineExceptionHandler { _, ex ->
                log(WARN) { "Error during launch: ${ex.asLog()}" }
                errorEvents.postValue(ex)
            }
        }

        return null
    }

    fun <T : Any> DynamicStateFlow<T>.asLiveData2() = flow.asLiveData2()

    fun <T> Flow<T>.asLiveData2() = this.asLiveData(context = getVMContext())

    fun launch(
        scope: CoroutineScope = viewModelScope,
        context: CoroutineContext = getVMContext(),
        block: suspend CoroutineScope.() -> Unit
    ) {
        try {
            scope.launch(context = context, block = block)
        } catch (e: CancellationException) {
            log(_tag, WARN) { "launch()ed coroutine was canceled (scope=$scope): ${e.asLog()}" }
        }
    }

    open fun <T> Flow<T>.launchInViewModel() = this
        .setupCommonEventHandlers(_tag) { "launchInViewModel()" }
        .launchIn(vmScope)

}