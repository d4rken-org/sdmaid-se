package eu.darken.sdmse.appcleaner.core.automation.specs

import android.annotation.TargetApi
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
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.Installed
import javax.inject.Inject

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@Reusable
open class OnePlus31PlusSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
) : OnePlus29PlusSpecs(ipcFunnel, context, deviceDetective) {

    override val label = TAG

    init {
        logTag = TAG
    }

    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (deviceDetective.isCustomROM()) return false
        // Oxygen OS 12
        return hasApiLevel(31) && deviceDetective.isOnePlus()
    }

    override fun getStorageEntryLabels(lang: String, script: String): Collection<String> {
        context.get3rdPartyString(AOSP_SETTINGS_PKG, "storage_use")?.let {
            log(TAG) { "Using label from APK: $it" }
            return setOf(it)
        }

        return when {
            else -> emptySet<String>()
        }.tryAppend { super.getStorageEntryLabels(lang, script) }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: OnePlus31PlusSpecs): AutomationStepGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "OnePlus29PlusSpecs")
    }

}
