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
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
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
    private val systemCleanerSettings: SystemCleanerSettings,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    val events = SingleLiveEvent<CustomFilterListEvents>()

    val state = combine(
        customFilterRepo.configs,
        upgradeRepo.upgradeInfo
            .map {
                @Suppress("USELESS_CAST")
                it as UpgradeRepo.Info?
            }
            .onStart { emit(null) },
        systemCleanerSettings.enabledCustomFilter.flow,
    ) { configs, upgradeInfo, enabledFilters ->
        val items = configs.map { config ->
            CustomFilterDefaultVH.Item(
                config = config,
                isEnabled = enabledFilters.contains(config.identifier),
                onItemClick = {
                    launch {
                        systemCleanerSettings.enabledCustomFilter.update {
                            val newId = config.identifier
                            if (it.contains(newId)) it - newId else it + newId
                        }
                    }
                },
                onEditClick = { edit(it) }
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

    fun edit(item: CustomFilterListAdapter.Item) = launch {
        log(TAG) { "edit($item)" }
        CustomFilterListFragmentDirections.actionCustomFilterListFragmentToCustomFilterEditorFragment(
            identifier = item.config.identifier
        ).navigate()
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "CustomFilter", "List", "ViewModel")
    }
}