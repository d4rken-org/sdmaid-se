package eu.darken.sdmse.common.uix

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.lifecycle.LiveData
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import eu.darken.sdmse.common.navigation.doNavigate
import eu.darken.sdmse.common.navigation.popBackStack


abstract class Fragment3(@LayoutRes layoutRes: Int?) : Fragment2(layoutRes) {

    constructor() : this(null)

    abstract val ui: ViewBinding?
    abstract val vm: ViewModel3

    var onErrorEvent: ((Throwable) -> Boolean)? = null

    var onFinishEvent: (() -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.navEvents.observe2(ui) {
            log(tag, VERBOSE) { "Nav event: $it" }

            it?.run { doNavigate(this) } ?: onFinishEvent?.invoke() ?: popBackStack()
        }

        vm.errorEvents.observe2(ui) {
            log(tag, VERBOSE) { "Error event: $it" }
            val showDialog = onErrorEvent?.invoke(it) ?: true
            if (showDialog) it.asErrorDialogBuilder(requireActivity()).show()
        }
    }

    inline fun <T> LiveData<T>.observe2(
        crossinline callback: (T) -> Unit
    ) {
        observe(viewLifecycleOwner) { callback.invoke(it) }
    }

    inline fun <T, reified VB : ViewBinding?> LiveData<T>.observe2(
        ui: VB,
        crossinline callback: VB.(T) -> Unit
    ) {
        observe(viewLifecycleOwner) { callback.invoke(ui, it) }
    }

}
