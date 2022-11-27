package eu.darken.sdmse.common

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.viewbinding.ViewBinding


fun <T> LiveData<T>.observe2(fragment: Fragment, callback: (T) -> Unit) {
    observe(fragment.viewLifecycleOwner) { callback.invoke(it) }
}

inline fun <T, reified VB : ViewBinding?> LiveData<T>.observe2(
    fragment: Fragment,
    ui: VB,
    crossinline callback: VB.(T) -> Unit
) {
    observe(fragment.viewLifecycleOwner) { callback.invoke(ui, it) }
}

fun <T> LiveData<T>.observe2(activity: AppCompatActivity, callback: (T) -> Unit) {
    observe(activity) { callback.invoke(it) }
}