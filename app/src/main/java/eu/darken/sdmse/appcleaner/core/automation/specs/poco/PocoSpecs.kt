package eu.darken.sdmse.appcleaner.core.automation.specs.poco

import android.content.Context
import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.automation.core.specs.ExplorerSpecGenerator
import eu.darken.sdmse.automation.core.specs.SpecGenerator
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.isInstalled
import eu.darken.sdmse.common.pkgs.toPkgId
import javax.inject.Inject

// TODO
@Reusable
class PocoSpecs @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    @ApplicationContext private val context: Context,
    private val deviceDetective: DeviceDetective,
    private val pkgRepo: PkgRepo,
) : ExplorerSpecGenerator() {

    override val label = TAG.toCaString()

    override val tag: String = TAG

    override suspend fun isResponsible(pkg: Installed): Boolean {
        if (deviceDetective.isCustomROM()) return false
        if (!hasApiLevel(31) || !deviceDetective.isPoco()) return false
        if (VERSION_STARTS.none { Build.VERSION.INCREMENTAL.startsWith(it) }) return false
        return pkgRepo.isInstalled(SETTINGS_PKG_MIUI)
    }

    override suspend fun getSpec(pkg: Installed): AutomationSpec {
        TODO("Not yet implemented")
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: PocoSpecs): SpecGenerator
    }

    companion object {
        val TAG: String = logTag("AppCleaner", "Automation", "PocoDynamicSpecs")

        private val SETTINGS_PKG_MIUI = "com.miui.securitycenter".toPkgId()
        private val SETTINGS_PKG_AOSP = "com.android.settings".toPkgId()

        private val VERSION_STARTS = arrayOf(
            "V12",
            // POCO/vayu_global/vayu:12/SKQ1.211006.001/V13.0.3.0.SJUMIXM:user/release-keys
            "V13",
            "V14",
        )
    }

}