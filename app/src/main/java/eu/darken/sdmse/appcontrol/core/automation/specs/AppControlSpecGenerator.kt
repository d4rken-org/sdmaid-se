package eu.darken.sdmse.appcontrol.core.automation.specs

import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.automation.core.specs.SpecGenerator
import eu.darken.sdmse.common.pkgs.features.Installed

interface AppControlSpecGenerator : SpecGenerator {
    suspend fun getForceStop(pkg: Installed): AutomationSpec
    suspend fun getArchive(pkg: Installed): AutomationSpec
}