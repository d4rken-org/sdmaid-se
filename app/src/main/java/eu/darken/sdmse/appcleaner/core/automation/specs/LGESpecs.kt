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
import java.util.*
import javax.inject.Inject

@Reusable
class LGESpecs @Inject constructor(
    ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
) : AOSP29PlusSpecs(ipcFunnel, context, deviceDetective) {

    override val label = TAG

    init {
        logTag = TAG
    }

    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (deviceDetective.isCustomROM()) return false
        return hasApiLevel(29) && deviceDetective.isLGE()
    }

    @Suppress("IntroduceWhenSubject")
    override fun getStorageEntryLabels(lang: String, script: String): Collection<String> {
        context.get3rdPartyString(AOSP_SETTINGS_PKG, "storage_settings")?.let {
            log(TAG) { "Using label from APK: $it" }
            return setOf(it)
        }

        return when {
            // https://github.com/d4rken/sdmaid-public/issues/3577
            // lge/judyln_lao_com/judyln:10/QKQ1.191222.002/2011502001bac:user/release-keys
            "en".toLang() == lang -> {
                listOf("Storage").tryPrepend { super.getStorageEntryLabels(lang, script) }
            }
            // LM-G810 10/QKQ1.200614.002
            "eu".toLang() == lang -> listOf("Memoria").tryAppend { super.getStorageEntryLabels(lang, script) }
            // lge/mh2lm/mh2lm:10/QKQ1.200216.002/20283101285e8:user/release-keys
            "pt".toLang() == lang -> listOf("Armazenamento").tryAppend { super.getStorageEntryLabels(lang, script) }
            else -> super.getStorageEntryLabels(lang, script)
        }
    }

    override fun getClearCacheButtonLabels(lang: String, script: String): Collection<String> {
        context.get3rdPartyString(AOSP_SETTINGS_PKG, "clear_cache_btn_text")?.let {
            log(TAG) { "Using label from APK: $it" }
            return setOf(it)
        }

        return when {
            // lge/mh2lm/mh2lm:10/QKQ1.200216.002/20283101285e8:user/release-keys
            "pt".toLang() == lang -> listOf("Apagar cache").tryAppend { super.getClearCacheButtonLabels(lang, script) }
            else -> super.getClearCacheButtonLabels(lang, script)
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: LGESpecs): AutomationStepGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "LGESpecs")
    }
}