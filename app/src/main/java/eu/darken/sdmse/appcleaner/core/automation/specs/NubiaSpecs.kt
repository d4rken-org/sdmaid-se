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
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.Installed
import javax.inject.Inject


@Reusable
class NubiaSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
) : AOSP29PlusSpecs(ipcFunnel, context, deviceDetective) {

    override val label = TAG

    init {
        logTag = TAG
    }

    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (deviceDetective.isCustomROM()) return false
        if (hasApiLevel(29) && !deviceDetective.isAndroidTV()) return false
        // nubia/NX659J/NX659J:10/QKQ1.200405.002/nubia.20201101.042008:user/release-keys
        return deviceDetective.isNubia()
    }

    override fun getStorageEntryLabels(lang: String, script: String): Collection<String> = when {
        "es".toLang() == lang -> setOf("Almacenamiento")
        else -> emptySet()
    }.tryAppend { super.getStorageEntryLabels(lang, script) }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: NubiaSpecs): AutomationStepGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "NubiaSpecs")
    }

}
