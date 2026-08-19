package eu.darken.sdmse.main.core.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.darken.sdmse.R
import eu.darken.sdmse.main.core.DashboardCardType
import eu.darken.sdmse.main.core.labelRes
import eu.darken.sdmse.main.core.shortcutIconRes
import eu.darken.sdmse.main.ui.shortcuts.ShortcutActivity

sealed class AppShortcut(
    val id: String,
    @StringRes val shortLabel: Int,
    @StringRes val longLabel: Int,
    @DrawableRes val iconRes: Int,
) {
    abstract fun createIntent(context: Context): Intent

    fun toShortcutInfo(context: Context): ShortcutInfo {
        return ShortcutInfo.Builder(context, id)
            .setShortLabel(context.getString(shortLabel))
            .setLongLabel(context.getString(longLabel))
            .setIcon(Icon.createWithResource(context, iconRes))
            .setIntent(createIntent(context))
            .build()
    }

    /**
     * A navigation shortcut for one of the dashboard's tools.
     *
     * The id is the lowercased enum name, which keeps AppControl's id byte-identical to the
     * previously hardcoded `"appcontrol"` shortcut, so an upgrade updates that entry in place
     * instead of orphaning pinned copies of it.
     */
    data class Tool(val type: DashboardCardType) : AppShortcut(
        id = type.name.lowercase(),
        shortLabel = type.labelRes,
        longLabel = type.labelRes,
        iconRes = type.shortcutIconRes,
    ) {
        override fun createIntent(context: Context): Intent = Intent(context, ShortcutActivity::class.java).apply {
            action = ShortcutActivity.ACTION_OPEN_TOOL
            putExtra(ShortcutActivity.EXTRA_TOOL, type.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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