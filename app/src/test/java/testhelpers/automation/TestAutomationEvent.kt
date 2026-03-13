package testhelpers.automation

import eu.darken.sdmse.automation.core.AutomationEvent
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import java.util.UUID

/**
 * Test implementation of [AutomationEvent] for testing automation specs.
 */
data class TestAutomationEvent(
    override val id: UUID = UUID.randomUUID(),
    override val pkgId: Pkg.Id,
    override val eventType: Int = android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
) : AutomationEvent {

    companion object {
        fun fromPkgId(pkgId: String): TestAutomationEvent = TestAutomationEvent(
            pkgId = pkgId.toPkgId()
        )
    }
}
