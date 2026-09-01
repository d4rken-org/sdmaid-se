package eu.darken.sdmse.common.pkgs

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.device.RomTypeProvider
import eu.darken.sdmse.common.pkgs.features.Installed
import javax.inject.Inject

/**
 * Single seam for detecting packages whose Android Settings details page can't be opened,
 * i.e. packages that accessibility based automation can never reach.
 *
 * Covers the structural [Installed.hasNoSettings] check (APEX/mainline) plus ROM specific
 * limitations. This is deliberately an automation-only concern: other deletion backends
 * (root, ADB) don't go through the Settings UI and must not be restricted by it.
 */
@Reusable
class NoSettingsDetector @Inject constructor(
    private val deviceDetective: DeviceDetective,
    private val romTypeProvider: RomTypeProvider,
) {

    /**
     * Why the settings page is unreachable. The distinction matters to the user:
     * [NO_SETTINGS_PAGE] is permanent, [DISABLED_APP] goes away by enabling the app.
     */
    enum class Reason {
        NO_SETTINGS_PAGE,
        DISABLED_APP,
    }

    suspend fun getUnreachableReason(pkg: Installed): Reason? = when {
        // Not installed for this user: there is no settings page to open, and no way to get one
        pkg.isHidden -> Reason.NO_SETTINGS_PAGE
        pkg.hasNoSettings -> Reason.NO_SETTINGS_PAGE
        // On One UI (Samsung) the settings page of disabled apps can't be opened
        !pkg.isEnabled && effectiveRomType() == RomType.ONEUI -> Reason.DISABLED_APP
        else -> null
    }.also { if (it != null) log(TAG, VERBOSE) { "Unreachable ($it): ${pkg.installId}" } }

    /**
     * The manual override wins: it exists so users can correct a misdetected OS when
     * accessibility automation misbehaves, which is exactly what this check feeds into.
     */
    private suspend fun effectiveRomType(): RomType {
        val override = romTypeProvider.getRomType()
        return if (override != RomType.AUTO) override else deviceDetective.getROMType()
    }

    companion object {
        private val TAG = logTag("Pkg", "NoSettingsDetector")
    }
}
