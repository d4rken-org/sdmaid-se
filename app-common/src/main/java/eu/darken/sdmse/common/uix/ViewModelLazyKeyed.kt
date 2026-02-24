package eu.darken.sdmse.common.uix

/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import kotlin.reflect.KClass

/**
 * Returns an existing ViewModel or creates a new one in the scope (usually, a fragment or
 * an activity), associated with this `ViewModelProvider`.
 *
 * @see ViewModelProvider.get(Class)
 */
//@MainThread
//inline fun <reified VM : ViewModel> ViewModelProvider.get() = get(VM::class.java)

/**
 * An implementation of [Lazy] used by [androidx.fragment.app.Fragment.viewModels] and
 * [androidx.activity.ComponentActivity.viewmodels].
 *
 * [storeProducer] is a lambda that will be called during initialization, [VM] will be created
 * in the scope of returned [ViewModelStore].
 *
 * [factoryProducer] is a lambda that will be called during initialization,
 * returned [ViewModelProvider.Factory] will be used for creation of [VM]
 */
class ViewModelLazyKeyed<VM : ViewModel>(
    private val viewModelClass: KClass<VM>,
    private val keyProducer: (() -> String)? = null,
    private val storeProducer: () -> ViewModelStore,
    private val factoryProducer: () -> ViewModelProvider.Factory
) : Lazy<VM> {
    private var cached: VM? = null

    override val value: VM
        get() {
            val viewModel = cached
            return if (viewModel == null) {
                val factory = factoryProducer()
                val store = storeProducer()
                val key = keyProducer?.invoke() ?: "androidx.lifecycle.ViewModelProvider.DefaultKey"
                ViewModelProvider(store, factory).get(
                    key + ":" + viewModelClass.java.canonicalName,
                    viewModelClass.java
                ).also {
                    cached = it
                }
            } else {
                viewModel
            }
        }

    override fun isInitialized() = cached != null
}

/**
 * Returns a property delegate to access [ViewModel] by **default** scoped to this [Fragment]:
 * ```
 * class MyFragment : Fragment() {
 *     val viewmodel: NYViewModel by viewmodels()
 * }
 * ```
 *
 * Custom [ViewModelProvider.Factory] can be defined via [factoryProducer] parameter,
 * factory returned by it will be used to create [ViewModel]:
 * ```
 * class MyFragment : Fragment() {
 *     val viewmodel: MYViewModel by viewmodels { myFactory }
 * }
 * ```
 *
 * Default scope may be overridden with parameter [ownerProducer]:
 * ```
 * class MyFragment : Fragment() {
 *     val viewmodel: MYViewModel by viewmodels ({requireParentFragment()})
 * }
 * ```
 *
 * This property can be accessed only after this Fragment is attached i.e., after
 * [Fragment.onAttach()], and access prior to that will result in IllegalArgumentException.
 */
@MainThread
inline fun <reified VM : ViewModel> Fragment.viewModelsKeyed(
    noinline keyProducer: (() -> String)? = null,
    noinline ownerProducer: () -> ViewModelStoreOwner = { this },
    noinline factoryProducer: (() -> ViewModelProvider.Factory)? = null
) = createViewModelLazyKeyed(VM::class, keyProducer, { ownerProducer().viewModelStore }, factoryProducer)

/**
 * Returns a property delegate to access parent activity's [ViewModel],
 * if [factoryProducer] is specified then [ViewModelProvider.Factory]
 * returned by it will be used to create [ViewModel] first time.
 *
 * ```
 * class MyFragment : Fragment() {
 *     val viewmodel: MyViewModel by activityViewModels()
 * }
 * ```
 *
 * This property can be accessed only after this Fragment is attached i.e., after
 * [Fragment.onAttach()], and access prior to that will result in IllegalArgumentException.
 */
@MainThread
inline fun <reified VM : ViewModel> Fragment.activityViewModelsKeyed(
    noinline keyProducer: (() -> String)? = null,
    noinline factoryProducer: (() -> ViewModelProvider.Factory)? = null
) = createViewModelLazyKeyed(VM::class, keyProducer, { requireActivity().viewModelStore }, factoryProducer)

/**
 * Helper method for creation of [ViewModelLazy], that resolves `null` passed as [factoryProducer]
 * to default factory.
 */
@MainThread
fun <VM : ViewModel> Fragment.createViewModelLazyKeyed(
    viewModelClass: KClass<VM>,
    keyProducer: (() -> String)? = null,
    storeProducer: () -> ViewModelStore,
    factoryProducer: (() -> ViewModelProvider.Factory)? = null
): Lazy<VM> {
    val factoryPromise = factoryProducer ?: {
        val application = activity?.application ?: throw IllegalStateException(
            "ViewModel can be accessed only when Fragment is attached"
        )
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
    return ViewModelLazyKeyed(viewModelClass, keyProducer, storeProducer, factoryPromise)
}

/**
 * Returns a [Lazy] delegate to access the ComponentActivity's ViewModel, if [factoryProducer]
 * is specified then [ViewModelProvider.Factory] returned by it will be used
 * to create [ViewModel] first time.
 *
 * ```
 * class MyComponentActivity : ComponentActivity() {
 *     val viewmodel: MyViewModel by viewmodels()
 * }
 * ```
 *
 * This property can be accessed only after the Activity is attached to the Application,
 * and access prior to that will result in IllegalArgumentException.
 */
@MainThread
inline fun <reified VM : ViewModel> ComponentActivity.viewModelsKeyed(
    noinline keyProducer: (() -> String)? = null,
    noinline factoryProducer: (() -> ViewModelProvider.Factory)? = null
): Lazy<VM> {
    val factoryPromise = factoryProducer ?: {
        val application = application ?: throw IllegalArgumentException(
            "ViewModel can be accessed only when Activity is attached"
        )
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }

    return ViewModelLazyKeyed(VM::class, keyProducer, { viewModelStore }, factoryPromise)
}
