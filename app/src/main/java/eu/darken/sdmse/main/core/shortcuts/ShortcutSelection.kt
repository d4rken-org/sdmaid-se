package eu.darken.sdmse.main.core.shortcuts

import eu.darken.sdmse.main.core.DashboardCardType

/**
 * Pure selection/ordering rules for the launcher shortcuts. Shared by the publisher
 * ([ShortcutManager]) and the picker UI so the two can never disagree about the order.
 */

/**
 * Turns a stored dashboard card order into a total order over [DashboardCardType]: first occurrence
 * of each stored type wins, then every type the stored order doesn't mention is appended in enum
 * order. The stored list is unconstrained serialized data, so it can omit or duplicate types
 * (fallbackToDefault only catches decode failures, not semantic gaps).
 */
fun normalizeCardOrder(cards: List<DashboardCardType>): List<DashboardCardType> {
    val stored = cards.distinct()
    return stored + DashboardCardType.entries.filter { !stored.contains(it) }
}

/** The selected tools, deduplicated and sorted by their position in the dashboard card order. */
fun orderedTools(
    enabled: Collection<DashboardCardType>,
    cardOrder: List<DashboardCardType>,
): List<DashboardCardType> {
    val order = normalizeCardOrder(cardOrder)
    return enabled.distinct().sortedBy { order.indexOf(it) }
}

/**
 * The shortcuts to publish: OneTap first when enabled (it is the action shortcut, the rest are
 * navigation), then the selected tools in dashboard card order.
 *
 * [maxCount] is a parameter rather than a framework lookup so this stays testable. It is defensive
 * only: the platform default is 15 and setDynamicShortcuts throws when exceeded, but the limit is
 * device-configurable.
 */
fun buildShortcuts(
    oneTapEnabled: Boolean,
    enabled: Collection<DashboardCardType>,
    cardOrder: List<DashboardCardType>,
    maxCount: Int,
): List<AppShortcut> = buildList {
    if (oneTapEnabled) add(AppShortcut.MainAction.OneTap)
    orderedTools(enabled, cardOrder).forEach { add(AppShortcut.Tool(it)) }
}.take(maxCount.coerceAtLeast(0))

/**
 * Maps an [AppShortcut.Tool] intent extra back to its type. Null-safe and lenient: the shortcut
 * trampoline is exported, so the value can be absent or garbage, and `valueOf` would throw.
 */
fun resolveShortcutTool(name: String?): DashboardCardType? =
    DashboardCardType.entries.firstOrNull { it.name == name }
