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
open class OnePlus29PlusSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
) : OnePlus14to28Specs(ipcFunnel, context, deviceDetective) {

    override val label = TAG

    init {
        logTag = TAG
    }

    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (deviceDetective.isCustomROM()) return false
        // Oxygen OS 11
        return hasApiLevel(29) && !hasApiLevel(31) && deviceDetective.isOnePlus()
    }

    override fun getStorageEntryLabels(lang: String, script: String): Collection<String> {
        // Fingerprint: OnePlus/OnePlus7/OnePlus7:11/RKQ1.201022.002/2206171327:user/release-keys
        // en_IN: Storage and cache
        // Fingerprint: OnePlus/DN2103EEA/OP515BL1:11/RP1A.200720.011/1653645228105:user/release-keys
        // de_DE: Speichernutzung

        // 5736:    <string name="storage_use">Speichernutzung</string>
        // 5834:    <string name="storageuse_settings_title">Speichernutzung</string>
        // 6294:    <string name="storage_use">Storage usage</string>
        setOf("storage_settings_for_app", "storage_use")
            .mapNotNull { context.get3rdPartyString(AOSP_SETTINGS_PKG, it) }
            .takeIf { it.isNotEmpty() }
            ?.let {
                log(TAG) { "Using label from APK: $it" }
                return it
            }

        return when {
            "de".toLang() == lang -> setOf(
                // OnePlus/DN2103EEA/OP515BL1:11/RP1A.200720.011/1632390704634:user/release-keys
                "Speichernutzung"
            )
            "en".toLang() == lang -> setOf(
                // OPPO/CPH2249/OP4F81L1:11/RP1A.200720.011/1632508695807:user/release-keys
                "Storage usage"
            )
            "ru".toLang() == lang -> setOf(
                // OnePlus/OnePlus9_IND/OnePlus9:12/SKQ1.210216.001/R.202201181959:user/release-keys ru_KZ,en_US
                "Использование памяти"
            )
            "pl".toLang() == lang -> setOf(
                "Pamięć i pamięć podręczna",
                // google/sunfish/sunfish:12/SP1A.211105.002/7743617:user/release-keys
                "Pamięć wewnętrzna i podręczna"
            )
            "ar".toLang() == lang -> setOf(
                // OnePlus/OnePlus7TPro/OnePlus7TPro:11/RKQ1.201022.002/2105071700:user/release-keys
                "مساحة التخزين وذاكرة التخزين المؤقت"
            )
            "ro".toLang() == lang -> setOf(
                // OnePlus/OnePlusNordCE_EEA/OnePlusNordCE:11/RKQ1.201217.002/2107220023:user/release-keys
                "Spațiul de stocare și memoria cache"
            )
            "es".toLang() == lang -> setOf(
                // OnePLUS A60003_22_1900712 @ Oxigen OS 9.0.5 (Android 10)
                "Almacenamiento y caché"
            )
            "it".toLang() == lang -> setOf(
                // OnePlus/OnePlus7Pro/OnePlus7Pro:10/QKQ1.190716.003/1909010630:user/release-keys
                "Spazio di archiviazione e cache"
            )
            else -> emptySet()
        }.tryAppend { super.getStorageEntryLabels(lang, script) }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: OnePlus29PlusSpecs): AutomationStepGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "OnePlus29PlusSpecs")
    }

}
