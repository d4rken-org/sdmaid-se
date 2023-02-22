package eu.darken.sdmse.automation.core

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import eu.darken.sdmse.automation.core.crawler.*
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.Installed
import timber.log.Timber
import java.util.*

interface SpecSource {
    val label: String

    suspend fun isResponsible(pkg: Installed): Boolean

    @Throws(UnsupportedOperationException::class)
    suspend fun getSpecs(pkg: Installed): List<ACCrawler.Step>

    fun Context.get3rdPartyString(packageName: String, stringIdName: String): String? = try {
        val appResources = packageManager.getResourcesForApplication(packageName)
        val identifier = appResources.getIdentifier(stringIdName, "string", packageName).takeIf { it != 0 }
        identifier?.let { appResources.getString(it) }.also {
            if (it != null) {
                Timber.d("Read %s:%s from settings APK: %s", packageName, stringIdName, it)
            } else {
                Timber.w("Failed to read %s:%s from settings APK.", packageName, stringIdName)
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "get3rdPartyString(%s, %s) failed", packageName, stringIdName)
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

        if (!Bugs.isArmed) {
            log(tag, WARN) { "DISARMED: Not clicking." }
            return@scope true
        }

        val success = CrawlerCommon.defaultClick(isArmed = Bugs.isArmed).invoke(node, retryCount)
        if (!success && !node.isEnabled) {
            Timber.tag(tag).d("Can't click on the clear cache button because it was disabled, but why...")
            try {
                val allButtonsAreDisabled = node.getRoot(maxNesting = 4).crawl().map { it.node }.all {
                    !it.isClickyButton() || !it.isEnabled
                }
                if (allButtonsAreDisabled) {
                    // https://github.com/d4rken/sdmaid-public/issues/3121
                    Timber.tag(tag)
                        .w("Clear cache button was disabled, but so are others, assuming size calculation going on.")
                    Timber.tag(tag).d("Sleeping for 1000ms to wait for calculation.")
                    Thread.sleep((500 * retryCount).toLong())
                    false
                } else {
                    // https://github.com/d4rken/sdmaid-public/issues/2517
                    Timber.tag(tag)
                        .w("Only the clear cache button was disabled, assuming stale information, counting as success.")
                    true
                }
            } catch (e: Exception) {
                Timber.tag(tag).w("Error while trying to determine why the clear cache button is not enabled.")
                false
            }
        } else {
            success
        }
    }

    fun tryCollection(source: () -> Collection<String>): Set<String> = try {
        source.invoke().toSet()
    } catch (e: Exception) {
        Timber.w(e, "Failed to source list.")
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