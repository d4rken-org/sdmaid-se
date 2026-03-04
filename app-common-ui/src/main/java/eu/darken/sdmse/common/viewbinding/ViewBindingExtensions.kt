package eu.darken.sdmse.common.viewbinding

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

inline fun <FragmentT : Fragment, reified BindingT : ViewBinding> FragmentT.viewBinding() =
    this.viewBinding(
        bindingProvider = {
            val bindingMethod = BindingT::class.java.getMethod("bind", View::class.java)
            bindingMethod(null, requireView()) as BindingT
        },
        lifecycleOwnerProvider = { viewLifecycleOwner }
    )

@Suppress("unused")
fun <FragmentT : Fragment, BindingT : ViewBinding> FragmentT.viewBinding(
    bindingProvider: FragmentT.() -> BindingT,
    lifecycleOwnerProvider: FragmentT.() -> LifecycleOwner
) = ViewBindingProperty(bindingProvider, lifecycleOwnerProvider)

class ViewBindingProperty<ComponentT : LifecycleOwner, BindingT : ViewBinding>(
    private val bindingProvider: (ComponentT) -> BindingT,
    private val lifecycleOwnerProvider: ComponentT.() -> LifecycleOwner
) : ReadOnlyProperty<ComponentT, BindingT> {

    private val uiHandler = Handler(Looper.getMainLooper())
    private var localRef: ComponentT? = null
    private var viewBinding: BindingT? = null

    private val onDestroyObserver = object : DefaultLifecycleObserver {
        // Called right before Fragment.onDestroyView
        override fun onDestroy(owner: LifecycleOwner) {
            localRef?.lifecycle?.removeObserver(this) ?: return

            localRef = null

            uiHandler.post {
                log(VERBOSE) { "Resetting viewBinding" }
                viewBinding = null
            }
        }
    }

    @MainThread
    override fun getValue(thisRef: ComponentT, property: KProperty<*>): BindingT {
        if (localRef == null && viewBinding != null) {
            log(WARN) { "Fragment.onDestroyView() was called, but the handler didn't execute our delayed reset." }
            /**
             * There is a fragment racecondition if you navigate to another fragment and quickly popBackStack().
             * Our uiHandler.post { } will not have executed for some reason.
             * In that case we manually null the old viewBinding, to allow for clean recreation.
             */
            viewBinding = null
        }

        /**
         * When quickly navigating, a fragment may be created that was never visible to the user.
         * It's possible that [Fragment.onDestroyView] is called, but [DefaultLifecycleObserver.onDestroy] is not.
         * This means the ViewBinding will is not be set to `null` and it still holds the previous layout,
         * instead of the new layout that the Fragment inflated when navigating back to it.
         */
        (localRef as? Fragment)?.view?.let {
            if (it != viewBinding?.root && localRef === thisRef) {
                log(WARN) { "Different view for the same fragment, resetting old viewBinding." }
                viewBinding = null
            }
        }

        viewBinding?.let {
            // Only accessible from within the same component
            require(localRef === thisRef)
            return@getValue it
        }

        val lifecycle = lifecycleOwnerProvider(thisRef).lifecycle

        return bindingProvider(thisRef).also {
            viewBinding = it
            localRef = thisRef
            lifecycle.addObserver(onDestroyObserver)
        }
    }
}