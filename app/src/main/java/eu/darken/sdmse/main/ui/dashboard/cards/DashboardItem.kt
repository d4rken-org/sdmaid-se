package eu.darken.sdmse.main.ui.dashboard.cards

import eu.darken.sdmse.common.lists.differ.DifferItem

sealed interface DashboardItem : DifferItem {
    override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
        get() = { old, new ->
            if (new::class.isInstance(old)) new else null
        }
}
