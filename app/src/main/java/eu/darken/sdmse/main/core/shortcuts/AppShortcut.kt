package eu.darken.sdmse.main.core.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.darken.sdmse.R
import eu.darken.sdmse.appcontrol.R as AppControlR
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.ui.shortcuts.ShortcutActivity

sealed class AppShortcut(
    val id: String,
    @StringRes val shortLabel: Int,
    @StringRes val longLabel: Int,
    @DrawableRes val iconRes: Int,
) {
    abstract fun createIntent(context: Context): Intent

    /**
     * [rank] is set explicitly: launchers only display the first few shortcuts, and list order alone
     * does not reliably define which ones those are.
     */
    fun toShortcutInfo(context: Context, rank: Int = 0): ShortcutInfo {
        return ShortcutInfo.Builder(context, id)
            .setShortLabel(context.getString(shortLabel))
            .setLongLabel(context.getString(longLabel))
            .setIcon(Icon.createWithResource(context, iconRes))
            .setIntent(createIntent(context))
            .setRank(rank)
            .build()
    }

    data object AppControl : AppShortcut(
        id = "appcontrol",
        shortLabel = AppControlR.string.shortcut_appcontrol_short,
        longLabel = AppControlR.string.shortcut_appcontrol_long,
        iconRes = R.drawable.ic_shortcut_apps
    ) {
        override fun createIntent(context: Context): Intent = Intent(context, ShortcutActivity::class.java).apply {
            action = ShortcutActivity.ACTION_OPEN_APPCONTROL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
    }

    /**
     * Runs a single tool's scan + delete straight from the launcher. Only the tools in
     * [OneTapCleaner.ONECLICK_TYPES] have a one-click task, so only those can have one.
     */
    data class ToolAction(val type: SDMTool.Type) : AppShortcut(
        id = "clean_" + type.name.lowercase(),
        shortLabel = type.cleanShortcutShortLabelRes,
        longLabel = type.cleanShortcutLongLabelRes,
        iconRes = type.cleanShortcutIconRes,
    ) {
        init {
            require(OneTapCleaner.ONECLICK_TYPES.contains(type)) { "$type has no clean shortcut" }
        }

        override fun createIntent(context: Context): Intent {
            // Read outside the apply block: inside it, `type` binds to Intent's own MIME type.
            val toolName = type.name
            return Intent(context, ShortcutActivity::class.java).apply {
                action = ShortcutActivity.ACTION_CLEAN_TOOL
                putExtra(ShortcutActivity.EXTRA_TOOL, toolName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }
    }

    sealed class MainAction(
        id: String,
        @StringRes shortLabel: Int,
        @StringRes longLabel: Int,
        @DrawableRes iconRes: Int,
    ) : AppShortcut(id, shortLabel, longLabel, iconRes) {

        override fun createIntent(context: Context): Intent = Intent(context, ShortcutActivity::class.java).apply {
            action = ShortcutActivity.ACTION_SCAN_DELETE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        object OneTap : MainAction(
            id = "onetap",
            shortLabel = R.string.shortcut_onetap_short,
            longLabel = R.string.shortcut_onetap_long,
            iconRes = R.drawable.ic_shortcut_onetap
        )
    }
}