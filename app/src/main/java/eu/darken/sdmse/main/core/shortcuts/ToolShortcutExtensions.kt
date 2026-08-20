package eu.darken.sdmse.main.core.shortcuts

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.darken.sdmse.R
import eu.darken.sdmse.main.core.SDMTool

/**
 * Resources for the per-tool "clean" launcher shortcuts. Only the tools in
 * [OneTapCleaner.ONECLICK_TYPES] can have one, because only those have a one-click task.
 */

@get:DrawableRes
val SDMTool.Type.cleanShortcutIconRes: Int
    get() = when (this) {
        SDMTool.Type.CORPSEFINDER -> R.drawable.ic_shortcut_corpsefinder
        SDMTool.Type.SYSTEMCLEANER -> R.drawable.ic_shortcut_systemcleaner
        SDMTool.Type.APPCLEANER -> R.drawable.ic_shortcut_appcleaner
        SDMTool.Type.DEDUPLICATOR -> R.drawable.ic_shortcut_deduplicator
        else -> throw IllegalArgumentException("$this has no clean shortcut")
    }

/** What the launcher menu shows. Launchers truncate this at roughly 10 characters. */
@get:StringRes
val SDMTool.Type.cleanShortcutShortLabelRes: Int
    get() = when (this) {
        SDMTool.Type.CORPSEFINDER -> R.string.shortcut_clean_corpsefinder_short
        SDMTool.Type.SYSTEMCLEANER -> R.string.shortcut_clean_systemcleaner_short
        SDMTool.Type.APPCLEANER -> R.string.shortcut_clean_appcleaner_short
        SDMTool.Type.DEDUPLICATOR -> R.string.shortcut_clean_deduplicator_short
        else -> throw IllegalArgumentException("$this has no clean shortcut")
    }

@get:StringRes
val SDMTool.Type.cleanShortcutLongLabelRes: Int
    get() = when (this) {
        SDMTool.Type.CORPSEFINDER -> R.string.shortcut_clean_corpsefinder_long
        SDMTool.Type.SYSTEMCLEANER -> R.string.shortcut_clean_systemcleaner_long
        SDMTool.Type.APPCLEANER -> R.string.shortcut_clean_appcleaner_long
        SDMTool.Type.DEDUPLICATOR -> R.string.shortcut_clean_deduplicator_long
        else -> throw IllegalArgumentException("$this has no clean shortcut")
    }

/**
 * Maps a [AppShortcut.ToolAction] intent extra back to its tool. Null-safe and lenient: the shortcut
 * trampoline is exported, so the value can be absent or garbage, and `valueOf` would throw. Only
 * tools that can actually have a clean shortcut resolve.
 */
fun resolveCleanShortcutTool(name: String?): SDMTool.Type? =
    OneTapCleaner.ONECLICK_TYPES.firstOrNull { it.name == name }
