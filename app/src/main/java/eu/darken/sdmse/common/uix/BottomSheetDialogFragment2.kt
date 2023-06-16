package eu.darken.sdmse.common.uix

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LiveData
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import eu.darken.sdmse.common.navigation.doNavigate
import eu.darken.sdmse.common.navigation.popBackStack
import eu.darken.sdmse.common.observe2


abstract class BottomSheetDialogFragment2 : BottomSheetDialogFragment() {

    abstract val ui: ViewBinding
    abstract val vm: ViewModel3

    internal val tag: String =
        logTag("Fragment", "${this.javaClass.simpleName}(${Integer.toHexString(hashCode())})")

    override fun onAttach(context: Context) {
        log(tag, VERBOSE) { "onAttach(context=$context)" }
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        log(tag, VERBOSE) { "onCreate(savedInstanceState=$savedInstanceState)" }
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        log(tag, VERBOSE) {
            "onCreateView(inflater=$inflater, container=$container, savedInstanceState=$savedInstanceState"
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        log(tag, VERBOSE) { "onViewCreated(view=$view, savedInstanceState=$savedInstanceState)" }
        super.onViewCreated(view, savedInstanceState)

        vm.navEvents.observe2(this, ui) { dir ->
            log(tag, VERBOSE) { "Nav event: $dir" }
            dir?.let { doNavigate(it) } ?: popBackStack()
        }
        vm.errorEvents.observe2(this, ui) {
            log(tag, VERBOSE) { "Error event: $it" }
            it.asErrorDialogBuilder(requireActivity()).show()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        log(tag, VERBOSE) { "onActivityCreated(savedInstanceState=$savedInstanceState)" }
        super.onActivityCreated(savedInstanceState)
    }

    override fun onResume() {
        log(tag, VERBOSE) { "onResume()" }
        super.onResume()
    }

    override fun onPause() {
        log(tag, VERBOSE) { "onPause()" }
        super.onPause()
    }

    override fun onDestroyView() {
        log(tag, VERBOSE) { "onDestroyView()" }
        super.onDestroyView()
    }

    override fun onDetach() {
        log(tag, VERBOSE) { "onDetach()" }
        super.onDetach()
    }

    override fun onDestroy() {
        log(tag, VERBOSE) { "onDestroy()" }
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        log(tag, VERBOSE) { "onActivityResult(requestCode=$requestCode, resultCode=$resultCode, data=$data)" }
        super.onActivityResult(requestCode, resultCode, data)
    }

    inline fun <T, reified VB : ViewBinding?> LiveData<T>.observe2(
        ui: VB,
        crossinline callback: VB.(T) -> Unit
    ) {
        observe(viewLifecycleOwner) { callback.invoke(ui, it) }
    }
}
