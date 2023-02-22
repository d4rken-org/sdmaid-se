package eu.darken.sdmse.appcleaner.core.automation.specs

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.automation.core.AutomationStepGenerator
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.Installed
import javax.inject.Inject

@Reusable
open class Samsung14To28Specs @Inject constructor(
    ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
) : AOSP14to28Specs(ipcFunnel, context, deviceDetective) {

    override val label = TAG

    init {
        logTag = TAG
    }

    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (deviceDetective.isCustomROM()) return false
        return !hasApiLevel(29) && deviceDetective.isSamsungDevice()
    }

    override fun getStorageEntryLabels(lang: String, script: String): Collection<String> {
        context.get3rdPartyString(AOSP_SETTINGS_PKG, "storage_settings")?.let {
            log(TAG) { "Using label from APK: $it" }
            return setOf(it)
        }

        return when {
            "zh-Hant".toLoc().let { it.language == lang && it.script == script } -> setOf(
                // Traditional
                "儲存位置"
            )
            "nl".toLang() == lang -> setOf(
                // samsung/beyond1ltexx/beyond1:9/PPR1.180610.011/G973FXXU3ASJD:user/release-keys
                "Opslag"
            )
            "ms".toLang() == lang -> setOf("Penyimpanan")
            "pl".toLang() == lang -> setOf("Domyślna Pamięć")
            "ar".toLang() == lang -> setOf("مكان التخزين")
            // samsung/a3y17ltexc/a3y17lte:8.0.0/R16NW/A320FLXXU3CRH2:user/release-keys
            "bg".toLang() == lang -> setOf("Устройство за съхранение на данни")
            else -> emptyList()
        }.tryAppend { super.getStorageEntryLabels(lang, script) }
    }

    override fun getClearCacheButtonLabels(lang: String, script: String): Collection<String> {
        context.get3rdPartyString(AOSP_SETTINGS_PKG, "clear_cache_btn_text")?.let {
            log(TAG) { "Using label from APK: $it" }
            return setOf(it)
        }

        return when {
            "zh-Hant".toLoc().let { it.language == lang && it.script == script } -> setOf(
                "清除緩存",
                // samsung/dream2ltexx/dream2lte:9/PPR1.180610.011/G955FXXU5DSHC:user/release-keys
                "清除快取"
            )
            "nl".toLang() == lang -> setOf(
                // samsung/beyond1ltexx/beyond1:9/PPR1.180610.011/G973FXXU3ASJD:user/release-keys
                "Cache legen"
            )
            "ja".toLang() == lang -> setOf("キャッシュを\u200B消去")
            "ms".toLang() == lang -> setOf("Padam cache")
            "pl".toLang() == lang -> setOf("Wyczyść Pamięć")
            "in".toLang() == lang -> setOf(
                // https://github.com/d4rken/sdmaid-public/issues/4785
                // samsung/j4primeltedx/j4primelte:9/PPR1.180610.011/J415FXXS6BTK2:user/release-keys
                "Hapus memori"
            )
            "ar".toLang() == lang -> setOf("مسح التخزين المؤقت", "مسح التخزين المؤقت\u202C")
            "cs".toLang() == lang -> setOf(
                // samsung/j5y17ltexx/j5y17lte:9/PPR1.180610.011/J530FXXS8CUE4:user/release-keys
                "Vymazat paměť"
            )
            "ro".toLang() == lang -> setOf(
                // samsung/dreamltexx/dreamlte:9/PPR1.180610.011/G950FXXUCDUD1:user/release-keys
                "Golire cache"
            )
            else -> emptyList()
        }.tryAppend { super.getClearCacheButtonLabels(lang, script) }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Samsung14To28Specs): AutomationStepGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "Samsung14To28Specs")
    }
}