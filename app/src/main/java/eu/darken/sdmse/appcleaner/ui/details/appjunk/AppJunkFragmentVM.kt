package eu.darken.sdmse.appcleaner.ui.details.appjunk

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementFileCategoryVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementFileVH
import eu.darken.sdmse.appcleaner.ui.details.appjunk.elements.AppJunkElementHeaderVH
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AppJunkFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val appCleaner: AppCleaner,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val args = AppJunkFragmentArgs.fromSavedStateHandle(handle)

    val info = appCleaner.data
        .filterNotNull()
        .map { data ->
            data.junks.singleOrNull { it.identifier == args.identifier }
        }
        .filterNotNull()
        .map { junk ->
            val elements = mutableListOf<AppJunkElementsAdapter.Item>()

            AppJunkElementHeaderVH.Item(
                appJunk = junk,
                onDeleteAllClicked = {
//                    TODO()
                },
                onExcludeClicked = {
//                    TODO()
                }
            ).run { elements.add(this) }

            junk.expendables
                ?.filter { it.value.isNotEmpty() }
                ?.map { (category, paths) ->
                    val categoryGroup = mutableListOf<AppJunkElementsAdapter.Item>()

                    AppJunkElementFileCategoryVH.Item(
                        appJunk = junk,
                        category = category,
                        paths = paths,
                        onItemClick = {
//                            TODO()
                        }
                    ).run { categoryGroup.add(this) }

                    paths
                        .map { lookup ->
                            AppJunkElementFileVH.Item(
                                appJunk = junk,
                                lookup = lookup,
                                onItemClick = {
//                                    TODO()
                                }
                            )
                        }
                        .run { categoryGroup.addAll(this) }

                    categoryGroup
                }
                ?.flatten()
                ?.run { elements.addAll(this) }

            Info(elements = elements)
        }
        .asLiveData2()

    data class Info(
        val elements: List<AppJunkElementsAdapter.Item>,
    )

    companion object {
        private val TAG = logTag("AppCleaner", "Details", "Fragment", "VM")
    }
}