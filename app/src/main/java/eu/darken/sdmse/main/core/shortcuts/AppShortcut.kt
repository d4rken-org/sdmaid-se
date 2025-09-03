package eu.darken.sdmse.main.core.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.darken.sdmse.R
import eu.darken.sdmse.main.ui.shortcuts.ShortcutActivity

sealed class AppShortcut(
    val id: String,
    @param:StringRes val shortLabel: Int,
    @param:StringRes val longLabel: Int,
    @param:DrawableRes val iconRes: Int,
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

    data object AppControl : AppShortcut(
        id = "appcontrol",
        shortLabel = R.string.shortcut_appcontrol_short,
        longLabel = R.string.shortcut_appcontrol_long,
        iconRes = R.drawable.ic_shortcut_apps
    ) {
        override fun createIntent(context: Context): Intent = Intent(context, ShortcutActivity::class.java).apply {
            action = ShortcutActivity.ACTION_OPEN_APPCONTROL
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