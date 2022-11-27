package eu.darken.sdmse.common.pkgs.picker.ui

import android.os.Bundle
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.smart.SmartFragment

interface PkgPickerListener {

    fun SmartFragment.setupPkgPickerListener(
        callback: (PkgPickerResult?) -> Unit
    ) {
        val fragment = this
        log { "setupPkgPickerListener(...) on ${fragment.hashCode()}" }
        setFragmentResultListener(RESULT_KEY) { key, bundle ->
            log { "setupPkgPickerListener() on ${fragment.hashCode()} -> $key - $bundle" }
            callback(bundle.getParcelable(RESULT_KEY))
        }
    }

    companion object {
        internal const val RESULT_KEY = "PkgPickerResult"
    }
}


fun PkgPickerFragment.setPkgPickerResult(
    result: PkgPickerResult?
) {
    log { "setPkgPickerResult(result=$result)" }
    setFragmentResult(
        PkgPickerListener.RESULT_KEY,
        Bundle().apply {
            putParcelable(PkgPickerListener.RESULT_KEY, result)
        }
    )
}