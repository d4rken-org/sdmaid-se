package eu.darken.sdmse.common.uix

import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModel
import eu.darken.sdmse.common.debug.logging.log

abstract class ViewModel1(
    open val tag: String = "ViewModel",
) : ViewModel() {

    @CallSuper
    override fun onCleared() {
        log(tag) { "onCleared()" }
        super.onCleared()
    }
}
