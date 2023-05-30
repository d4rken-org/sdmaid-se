package eu.darken.sdmse.automation.core.common

import android.annotation.SuppressLint
import android.content.Context
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.pkgs.Pkg
import java.util.Locale

interface AutomationLabelSource {
    fun String.toLoc(): Locale = Locale.forLanguageTag(this)

    fun String.toLang(): String = this.toLoc().language

    fun String.toScript(): String = this.toLoc().script

    @SuppressLint("DiscouragedApi")
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

    fun tryCollection(source: () -> Collection<String>): Set<String> = try {
        source.invoke().toSet()
    } catch (e: Exception) {
        log(WARN) { "Failed to source list: ${e.asLog()}" }
        emptySet()
    }

    fun Collection<String>.tryPrepend(source: () -> Collection<String>): Set<String> =
        tryCollection(source).plus(this).toSet()

    fun Collection<String>.tryAppend(source: () -> Collection<String>): Set<String> =
        this.plus(tryCollection(source)).toSet()
}