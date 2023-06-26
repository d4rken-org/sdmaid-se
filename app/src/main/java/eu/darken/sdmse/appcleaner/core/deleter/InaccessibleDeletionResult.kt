package eu.darken.sdmse.appcleaner.core.deleter

import eu.darken.sdmse.common.pkgs.features.Installed

interface InaccessibleDeletionResult {
    val successful: Collection<Installed.InstallId>
    val failed: Collection<Installed.InstallId>
}