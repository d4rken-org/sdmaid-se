package eu.darken.sdmse.common.uix

import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModel
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag

abstract class ViewModel1 : ViewModel() {
   internal val _tag: String = logTag("ViewModel", javaClass.simpleName)

    init {
        log(_tag) { "Initialized" }
    }

    @CallSuper
    override fun onCleared() {
        log(_tag) { "onCleared()" }
        super.onCleared()
    }
}