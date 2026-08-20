package eu.darken.sdmse.main.core.shortcuts

import eu.darken.sdmse.main.core.SDMTool

/**
 * Pure selection/ordering rules for the launcher shortcuts. Shared by the publisher
 * ([ShortcutManager]) and its tests so the two can never disagree about the order.
 *
 * OneTap first when enabled, then AppControl, then the enabled per-tool clean actions in
 * [OneTapCleaner.ONECLICK_TYPES] order. [maxCount] is a parameter rather than a framework lookup so
 * this stays testable. It is defensive only: the platform default is 15 and setDynamicShortcuts
 * throws when exceeded, but the limit is device-configurable.
 */
fun buildShortcuts(
    oneTapEnabled: Boolean,
    cleanTools: Collection<SDMTool.Type>,
    maxCount: Int,
): List<AppShortcut> = buildList<AppShortcut> {
    if (oneTapEnabled) add(AppShortcut.MainAction.OneTap)
    add(AppShortcut.AppControl)
    OneTapCleaner.ONECLICK_TYPES
        .filter { cleanTools.contains(it) }
        .forEach { add(AppShortcut.ToolAction(it)) }
}.take(maxCount.coerceAtLeast(0))
