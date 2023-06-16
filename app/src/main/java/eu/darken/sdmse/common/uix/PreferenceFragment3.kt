package eu.darken.sdmse.common.uix

import android.os.Bundle
import android.view.View
import androidx.lifecycle.LiveData
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.common.error.asErrorDialogBuilder

abstract class PreferenceFragment3 : PreferenceFragment2() {

    abstract val vm: ViewModel3

    var onErrorEvent: ((Throwable) -> Boolean)? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.errorEvents.observe2 {
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