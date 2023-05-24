package eu.darken.sdmse.automation.core

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import eu.darken.sdmse.automation.core.crawler.*
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed
import java.util.*

interface AutomationStepGenerator {
    val label: String

    suspend fun isResponsible(pkg: Installed): Boolean

    @Throws(UnsupportedOperationException::class)
    suspend fun getSpecs(pkg: Installed): List<AutomationCrawler.Step>

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

    fun String.toLoc(): Locale = Locale.forLanguageTag(this)

    fun String.toLang(): String = this.toLoc().language

    fun String.toScript(): String = this.toLoc().script

    fun getDefaultClearCacheClick(
        pkg: Installed,
        tag: String
    ): (AccessibilityNodeInfo, Int) -> Boolean = scope@{ node, retryCount ->
        log(tag, VERBOSE) { "Clicking on ${node.toStringShort()} for $pkg:" }

        if (Bugs.isDryRun) {
            log(tag, WARN) { "DRYRUN: Not clicking ${node.toStringShort()}" }
            return@scope true
        }

        val success = CrawlerCommon.defaultClick(isDryRun = Bugs.isDryRun).invoke(node, retryCount)
        if (!success && !node.isEnabled) {
            log(tag) { "Can't click on the clear cache button because it was disabled, but why..." }
            try {
                val allButtonsAreDisabled = node.getRoot(maxNesting = 4).crawl().map { it.node }.all {
                    !it.isClickyButton() || !it.isEnabled
                }
                if (allButtonsAreDisabled) {
                    // https://github.com/d4rken/sdmaid-public/issues/3121
                    log(tag, WARN) {
                        "Clear cache button was disabled, but so are others, assuming size calculation going on."
                    }
                    log(tag) { "Sleeping for 1000ms to wait for calculation." }
                    Thread.sleep((500 * retryCount).toLong())
                    false
                } else {
                    // https://github.com/d4rken/sdmaid-public/issues/2517
                    log(tag, WARN) {
                        "Only the clear cache button was disabled, assuming stale information, counting as success."
                    }
                    true
                }
            } catch (e: Exception) {
                log(tag, WARN) { "Error while trying to determine why the clear cache button is not enabled." }
                false
            }
        } else {
            success
        }
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

    companion object {
        fun getSysLocale(): Locale {
            return if (hasApiLevel(24)) {
                @Suppress("NewApi")
                Resources.getSystem().configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                Resources.getSystem().configuration.locale
            }
        }

        fun createUnsupportedError(tag: String): Throwable {
            val locale = getSysLocale()
            val apiLevel = BuildWrap.VERSION.SDK_INT
            val rom = Build.MANUFACTURER
            return UnsupportedOperationException("$tag: ROM $rom($apiLevel) & Locale ($locale) is not supported.")
        }
    }
}