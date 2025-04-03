package eu.darken.sdmse.automation.core.common

import android.content.res.Resources
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import java.util.Locale

fun AutomationExplorer.Context.getSysLocale(): Locale {
    val locales = Resources.getSystem().configuration.locales
    log(INFO) { "getSysLocale(): $locales" }
    return locales[0]
}