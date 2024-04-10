package eu.darken.sdmse.appcleaner.core.automation.specs

import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.automation.core.specs.SpecGenerator
import eu.darken.sdmse.common.pkgs.features.Installed

interface AppCleanerSpecGenerator : SpecGenerator {

    suspend fun getClearCache(pkg: Installed): AutomationSpec
}