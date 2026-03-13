package eu.darken.sdmse.main.ui.settings.cards

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.DashboardCardConfig
import eu.darken.sdmse.main.core.GeneralSettings
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class DashboardCardConfigViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
) : ViewModel3(dispatcherProvider) {

    val state = generalSettings.dashboardCardConfig.flow.map { config ->
        val cardItems = config.cards.mapIndexed { index, entry ->
            DashboardCardConfigAdapter.CardItem(
                cardEntry = entry,
                position = index,
                onVisibilityToggle = { toggleVisibility(it) },
            )
        }
        State(
            items = listOf(DashboardCardConfigAdapter.HeaderItem()) + cardItems,
        )
    }.asLiveData2()

    data class State(
        val items: List<DashboardCardConfigAdapter.Item>,
    )

    fun onItemsReordered(items: List<DashboardCardConfigAdapter.Item>) = launch {
        val cardItems = items.filterIsInstance<DashboardCardConfigAdapter.CardItem>()
        log(TAG) { "onItemsReordered(): ${cardItems.map { it.cardEntry.type }}" }
        val newConfig = DashboardCardConfig(
            cards = cardItems.map { it.cardEntry }
        )
        generalSettings.dashboardCardConfig.value(newConfig)
    }

    private fun toggleVisibility(item: DashboardCardConfigAdapter.CardItem) = launch {
        log(TAG) { "toggleVisibility(): ${item.cardEntry.type} -> ${!item.cardEntry.isVisible}" }
        val currentConfig = generalSettings.dashboardCardConfig.value()
        val newCards = currentConfig.cards.map { entry ->
            if (entry.type == item.cardEntry.type) {
                entry.copy(isVisible = !entry.isVisible)
            } else {
                entry
            }
        }
        generalSettings.dashboardCardConfig.value(DashboardCardConfig(cards = newCards))
    }

    fun resetToDefaults() = launch {
        log(TAG) { "resetToDefaults()" }
        generalSettings.dashboardCardConfig.value(DashboardCardConfig())
    }

    companion object {
        private val TAG = logTag("Dashboard", "CardConfig", "ViewModel")
    }
}
