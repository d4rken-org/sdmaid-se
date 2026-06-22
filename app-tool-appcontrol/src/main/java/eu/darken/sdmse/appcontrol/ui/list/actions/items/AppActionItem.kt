package eu.darken.sdmse.appcontrol.ui.list.actions.items

import eu.darken.sdmse.appcontrol.core.usage.UsageInfo
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.exclusion.core.types.ExclusionId

sealed interface AppActionItem {
    sealed interface Info : AppActionItem {
        data class Size(val sizes: PkgOps.SizeStats) : Info
        data class Usage(
            val installedAt: java.time.Instant?,
            val updatedAt: java.time.Instant?,
            val usage: UsageInfo?,
        ) : Info
    }

    sealed interface Action : AppActionItem {
        val installId: InstallId

        data class Launch(override val installId: InstallId) : Action
        data class ForceStop(override val installId: InstallId) : Action
        data class SystemSettings(override val installId: InstallId) : Action
        data class AppStore(override val installId: InstallId) : Action
        data class Exclude(
            override val installId: InstallId,
            val existingExclusionId: ExclusionId?,
        ) : Action

        data class Toggle(
            override val installId: InstallId,
            val isEnabled: Boolean,
        ) : Action

        data class Uninstall(override val installId: InstallId) : Action
        data class Archive(override val installId: InstallId) : Action
        data class Restore(override val installId: InstallId) : Action
        data class Export(override val installId: InstallId) : Action
    }
}
