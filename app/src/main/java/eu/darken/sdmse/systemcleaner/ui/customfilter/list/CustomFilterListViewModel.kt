package eu.darken.sdmse.systemcleaner.ui.customfilter.list

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.main.ui.dashboard.items.*
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterRepo
import eu.darken.sdmse.systemcleaner.ui.customfilter.list.types.CustomFilterDefaultVH
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class CustomFilterListViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val customFilterRepo: CustomFilterRepo,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val events = SingleLiveEvent<CustomFilterListEvents>()

    val state = combine(
        customFilterRepo.configs,
        upgradeRepo.upgradeInfo.map { it }.onStart { emit(null) },
    ) { configs, upgradeInfo ->
        val items = configs.map { config ->
            CustomFilterDefaultVH.Item(
                config = config,
                onItemClick = {
                    // TODO go to edit screen
                }
            )
        }
        val sortedItems = items.sortedBy { it.config.createdAt }
        State(
            sortedItems,
            loading = false,
            isPro = upgradeInfo?.isPro
        )
    }
        .onStart { emit(State()) }
        .asLiveData2()

    data class State(
        val items: List<CustomFilterListAdapter.Item> = emptyList(),
        val loading: Boolean = true,
        val isPro: Boolean? = null,
    )

    fun restore(items: Set<CustomFilterConfig>) = launch {
        log(TAG) { "restore(${items.size})" }
        customFilterRepo.save(items)
    }

    fun remove(items: List<CustomFilterListAdapter.Item>) = launch {
        log(TAG) { "remove(${items.size})" }
        val configs = items.map { it.config }.toSet()
        customFilterRepo.remove(configs.map { it.identifier }.toSet())
        events.postValue(CustomFilterListEvents.UndoRemove(configs))
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "CustomFilter", "List", "ViewModel")
    }
}