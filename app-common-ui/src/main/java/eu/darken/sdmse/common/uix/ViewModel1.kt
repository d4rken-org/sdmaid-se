package eu.darken.sdmse.common.uix

import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModel
import eu.darken.sdmse.common.debug.logging.log

abstract class ViewModel1(
    open val tag: String = "ViewModel",
) : ViewModel() {

    // FIXME: Remove after Compose rewrite — use `tag` instead
    @Deprecated("Use tag instead", ReplaceWith("tag"))
    internal val _tag: String get() = tag

    @CallSuper
    override fun onCleared() {
        log(tag) { "onCleared()" }
        super.onCleared()
    }
}
