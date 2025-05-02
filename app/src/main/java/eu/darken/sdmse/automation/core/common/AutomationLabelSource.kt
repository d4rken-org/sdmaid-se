package eu.darken.sdmse.automation.core.common

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.getLocales
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.Pkg
import java.util.Locale

interface AutomationLabelSource {

    fun AutomationExplorer.Context.getStrings(pkgId: Pkg.Id, keys: Set<String>): Set<String> {
        return getLocales()
            .map { locale -> keys.map { locale to it } }
            .flatten()
            .mapNotNull { (loc, resId) -> host.service.get3rdPartyString(pkgId, resId, loc) }
            .toSet()
    }

    fun String.toLoc(): Locale = Locale.forLanguageTag(this)

    fun String.toLang(): String = this.toLoc().language

    fun String.toScript(): String = this.toLoc().script


    @SuppressLint("DiscouragedApi")
    fun Context.get3rdPartyString(pkgId: Pkg.Id, stringIdName: String, locale: Locale): String? = try {
        val origRes = packageManager.getResourcesForApplication(pkgId.name)
        val config = Configuration(origRes.configuration).apply {
            setLocale(locale)
        }

        @Suppress("DEPRECATION")
        val localizedRes = Resources(origRes.assets, origRes.displayMetrics, config)

        val identifier = localizedRes.getIdentifier(stringIdName, "string", pkgId.name).takeIf { it != 0 }
        identifier?.let { localizedRes.getString(it) }.also {
            if (it != null) {
                log { "Read ${pkgId.name}:${stringIdName} [$locale] from settings APK: $it" }
            } else {
                log(WARN) { "Failed to read ${pkgId.name}:${stringIdName} [$locale] from settings APK." }
            }
        }
    } catch (e: Exception) {
        log(ERROR) { "get3rdPartyString(${pkgId.name}, $stringIdName, $locale) failed: ${e.asLog()}" }
        null
    }

    fun Collection<String>.append(source: () -> Collection<String>): Set<String> =
        this.plus(
            try {
                source.invoke().toSet()
            } catch (e: Exception) {
                log(WARN) { "Failed to source list: ${e.asLog()}" }
                emptySet()
            }
        ).toSet()

    companion object {
        internal val TAG = logTag("Automation", "LabelSource")
    }
}