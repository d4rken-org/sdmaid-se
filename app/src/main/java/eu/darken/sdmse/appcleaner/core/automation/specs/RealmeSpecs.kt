package eu.darken.sdmse.appcleaner.core.automation.specs

import android.content.Context
import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.automation.core.AutomationStepGenerator
import eu.darken.sdmse.automation.core.crawler.CrawlerCommon
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.features.Installed
import java.util.*
import javax.inject.Inject

@Reusable
class RealmeSpecs @Inject constructor(
    ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
) : AOSP14to28Specs(ipcFunnel, context, deviceDetective) {

    override val label = TAG

    init {
        logTag = TAG
        sourceClearCacheWindowNodeTest = { _: Installed, _: Locale ->
            CrawlerCommon.windowCriteria(AOSP_SETTINGS_PKG)
        }
    }

    // https://github.com/d4rken/sdmaid-public/issues/3040
    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (deviceDetective.isCustomROM()) return false
        return Build.MANUFACTURER.lowercase(Locale.ROOT) == "realme"
    }

    override fun getStorageEntryLabels(lang: String, script: String): Collection<String> {
        context.get3rdPartyString(AOSP_SETTINGS_PKG, "storage_use")?.let {
            log(TAG) { "Using label from APK: $it" }
            return setOf(it)
        }
        return when {
            "en".toLang() == lang -> setOf("Storage usage")
            "de".toLang() == lang -> setOf("Speichernutzung")
            "fil".toLang() == lang -> setOf("Paggamit ng storage")
            "ru".toLang() == lang -> setOf("Использование памяти")
            "es".toLang() == lang -> setOf(
                // realme/RMX1993EEA/RMX1993L1:11/RKQ1.201112.002/1621946429926:user/release-keys
                "Uso de almacenamiento",
                // realme/RMX2155EEA/RMX2155L1:11/RP1A.200720.011/1634016814456:user/release-keys
                "Uso del almacenamiento"
            )
            "ru".toLang() == lang -> setOf(
                // realme/RMX3301EEA/RED8ACL1:12/SKQ1.211019.001/S.202203141808:user/release-keys
                "Utilizzo memoria"
            )
            else -> emptySet()
        }.tryAppend { super.getStorageEntryLabels(lang, script) }
    }

    override fun getClearCacheButtonLabels(lang: String, script: String): Collection<String> {
        context.get3rdPartyString(AOSP_SETTINGS_PKG, "clear_cache_btn_text")?.let {
            log(TAG) { "Using label from APK: $it" }
            return setOf(it)
        }
        return when {
            "es".toLang() == lang -> setOf(
                // realme/RMX2155EEA/RMX2155L1:11/RP1A.200720.011/1634016814456:user/release-keys
                "Borrar caché"
            )
            "it".toLang() == lang -> setOf("Cancella cache")
            else -> emptySet()
        }.tryAppend { super.getClearCacheButtonLabels(lang, script) }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: RealmeSpecs): AutomationStepGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "RealmeSpecs")
    }
}