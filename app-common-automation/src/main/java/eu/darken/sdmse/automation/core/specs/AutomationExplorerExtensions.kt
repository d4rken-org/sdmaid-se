package eu.darken.sdmse.automation.core.specs

import android.content.res.Resources
import android.view.View
import eu.darken.sdmse.common.locale.toList


fun AutomationExplorer.Context.getLocales() = Resources.getSystem().configuration.locales.toList()

fun AutomationExplorer.Context.isSystemLayoutRtl(): Boolean =
    Resources.getSystem().configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
