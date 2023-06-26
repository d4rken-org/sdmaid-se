package eu.darken.sdmse.appcleaner.core.deleter

import eu.darken.sdmse.common.pkgs.features.Installed

data class ShizukuDeletionResult(
    override val successful: Collection<Installed.InstallId>,
    override val failed: Collection<Installed.InstallId>,
) : InaccessibleDeletionResult