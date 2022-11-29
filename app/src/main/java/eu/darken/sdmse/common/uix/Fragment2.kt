package eu.darken.sdmse.common.uix

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.doNavigate


abstract class Fragment2(@LayoutRes val layoutRes: Int?) : Fragment(layoutRes ?: 0) {

    constructor() : this(null)

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
        return layoutRes?.let { inflater.inflate(it, container, false) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        log(tag, VERBOSE) { "onViewCreated(view=$view, savedInstanceState=$savedInstanceState)" }
        super.onViewCreated(view, savedInstanceState)
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

    fun NavDirections.navigate() = doNavigate(this)
}
