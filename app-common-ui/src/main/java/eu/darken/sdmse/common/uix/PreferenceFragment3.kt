package eu.darken.sdmse.common.uix

import android.os.Bundle
import android.view.View
import androidx.lifecycle.LiveData
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import eu.darken.sdmse.common.preferences.MaterialListPreference

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

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is ListPreference) {
            showListPreferenceDialog(preference)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    private fun showListPreferenceDialog(preference: ListPreference) {
        val dialogFragment = MaterialListPreference()
        val bundle = Bundle(1).apply {
            putString("key", preference.key)
        }
        dialogFragment.arguments = bundle
        @Suppress("DEPRECATION")
        dialogFragment.setTargetFragment(this, 0)
        dialogFragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
    }
}