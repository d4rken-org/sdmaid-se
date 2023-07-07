package eu.darken.sdmse.automation.core.specs

import android.content.Context
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed

interface SpecGenerator {
    val label: CaString

    suspend fun isResponsible(pkg: Installed): Boolean

    suspend fun getSpec(pkg: Installed): AutomationSpec

    fun Context.get3rdPartyString(pkgId: Pkg.Id, stringIdName: String): String? = try {
        val appResources = packageManager.getResourcesForApplication(pkgId.name)
        val identifier = appResources.getIdentifier(stringIdName, "string", pkgId.name).takeIf { it != 0 }
        identifier?.let { appResources.getString(it) }.also {
            if (it != null) {
                log { "Read ${pkgId.name}:${stringIdName} from settings APK: $it" }
            } else {
                log(WARN) { "Failed to read ${pkgId.name}:${stringIdName} from settings APK." }
            }
        }
    } catch (e: Exception) {
        log(ERROR) { "get3rdPartyString(${pkgId.name}, $stringIdName) failed: ${e.asLog()}" }
        null
    }
}