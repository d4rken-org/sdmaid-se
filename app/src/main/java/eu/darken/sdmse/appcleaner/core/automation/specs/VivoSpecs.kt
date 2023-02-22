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
import java.util.*
import javax.inject.Inject


@Reusable
open class VivoSpecs @Inject constructor(
    ipcFunnel: IPCFunnel,
    @ApplicationContext context: Context,
    private val deviceDetective: DeviceDetective,
) : AOSP14to28Specs(ipcFunnel, context, deviceDetective) {

    override val label = TAG

    init {
        logTag = TAG
    }

    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (deviceDetective.isCustomROM()) return false
        if (hasApiLevel(29)) return false
        return deviceDetective.isVivo()
    }

    @Suppress("IntroduceWhenSubject")
    override fun getClearCacheButtonLabels(lang: String, script: String): Collection<String> = when {
        // vivo/1808/1808:8.1.0/O11019/1592484538:user/release-keys
        // https://github.com/d4rken/sdmaid-public/issues/3670
        "in".toLang() == lang -> setOf("Bersihkan cache")
        else -> emptyList()
    }.tryAppend { super.getClearCacheButtonLabels(lang, script) }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: VivoSpecs): AutomationStepGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "VivioSpecs")
    }
}