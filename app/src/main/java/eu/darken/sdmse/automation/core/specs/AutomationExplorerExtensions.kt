package eu.darken.sdmse.automation.core.specs

import android.content.res.Resources
import eu.darken.sdmse.common.locale.toList


fun AutomationExplorer.Context.getLocales() = Resources.getSystem().configuration.locales.toList()