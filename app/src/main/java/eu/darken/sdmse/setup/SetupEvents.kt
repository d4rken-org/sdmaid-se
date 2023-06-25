package eu.darken.sdmse.setup

import android.content.Intent
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.setup.automation.AutomationSetupModule
import eu.darken.sdmse.setup.saf.SAFSetupModule

sealed interface SetupEvents {
    data class SafRequestAccess(
        val item: SAFSetupModule.State.PathAccess,
    ) : SetupEvents

    data class SafWrongPathError(
        val exception: Exception
    ) : SetupEvents

    data class RuntimePermissionRequests(
        val item: Permission,
    ) : SetupEvents

    data class ConfigureAccessibilityService(
        val item: AutomationSetupModule.State,
    ) : SetupEvents

    data class ShowOurDetailsPage(
        val intent: Intent,
    ) : SetupEvents
}
