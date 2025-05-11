package eu.darken.sdmse.appcleaner.core.automation.specs.oxygenos

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.automation.specs.AppCleanerLabelSource
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.getLocales
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
open class OxygenOSLabels29Plus @Inject constructor(
    private val oxygenOSLabels14Plus: OxygenOSLabels14Plus,
) : AppCleanerLabelSource {

    fun getStorageEntryDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> {
        // Fingerprint: OnePlus/OnePlus7/OnePlus7:11/RKQ1.201022.002/2206171327:user/release-keys
        // en_IN: Storage and cache
        // Fingerprint: OnePlus/DN2103EEA/OP515BL1:11/RP1A.200720.011/1653645228105:user/release-keys
        // de_DE: Speichernutzung

        // 5736:    <string name="storage_use">Speichernutzung</string>
        // 5834:    <string name="storageuse_settings_title">Speichernutzung</string>
        // 6294:    <string name="storage_use">Storage usage</string>
        return acsContext.getStrings(SETTINGS_PKG, setOf("storage_settings_for_app", "storage_use"))
    }

    fun getStorageEntryLabels(
        acsContext: AutomationExplorer.Context
    ): Set<String> = acsContext.getLocales()
        .map { it.language to it.script }
        .mapNotNull { (lang, _) ->
            when {
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

                else -> null
            }
        }
        .flatten()
        .append { oxygenOSLabels14Plus.getStorageEntryLabels(acsContext) }
        .toSet()

    fun getClearCacheDynamic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = oxygenOSLabels14Plus.getClearCacheDynamic(acsContext)

    fun getClearCacheStatic(
        acsContext: AutomationExplorer.Context
    ): Set<String> = oxygenOSLabels14Plus.getClearCacheStatic(acsContext)

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "OnePlus", "Labels", "29Plus")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }

}
