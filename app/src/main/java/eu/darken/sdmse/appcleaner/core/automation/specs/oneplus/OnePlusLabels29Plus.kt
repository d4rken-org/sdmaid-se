package eu.darken.sdmse.appcleaner.core.automation.specs.oneplus

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.common.AutomationLabelSource
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

@Reusable
open class OnePlusLabels29Plus @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onePlusLabels14Plus: OnePlusLabels14Plus,
) : AutomationLabelSource {

    fun getStorageEntryLabel(): Collection<String>? {
        // Fingerprint: OnePlus/OnePlus7/OnePlus7:11/RKQ1.201022.002/2206171327:user/release-keys
        // en_IN: Storage and cache
        // Fingerprint: OnePlus/DN2103EEA/OP515BL1:11/RP1A.200720.011/1653645228105:user/release-keys
        // de_DE: Speichernutzung

        // 5736:    <string name="storage_use">Speichernutzung</string>
        // 5834:    <string name="storageuse_settings_title">Speichernutzung</string>
        // 6294:    <string name="storage_use">Storage usage</string>
        return setOf("storage_settings_for_app", "storage_use")
            .mapNotNull { context.get3rdPartyString(SETTINGS_PKG, it) }
            .takeIf { it.isNotEmpty() }
            .also { log(TAG) { "getStorageEntryLabel(): $it" } }
    }

    fun getStorageEntryLabels(lang: String, script: String): Collection<String> = when {
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
    }.tryAppend { onePlusLabels14Plus.getStorageEntryLabels(lang, script) }

    fun getClearCacheDynamic(): Set<String>? = onePlusLabels14Plus.getClearCacheDynamic()

    fun getClearCacheStatic(lang: String, script: String): Set<String> =
        onePlusLabels14Plus.getClearCacheStatic(lang, script)

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "OnePlus", "Labels", "29Plus")
        val SETTINGS_PKG = "com.android.settings".toPkgId()
    }

}
