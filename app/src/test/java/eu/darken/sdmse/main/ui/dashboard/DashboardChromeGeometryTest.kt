package eu.darken.sdmse.main.ui.dashboard

import androidx.compose.foundation.shape.RoundedCornerShape
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Guards the fix for the "empty notch" bug: the bar must only carve out the FAB cutout when the FAB
 * is actually present (i.e. when the dashboard is ready). See [dashboardBarShape].
 */
class DashboardChromeGeometryTest : BaseTest() {

    @Test
    fun `ready bar carves the FAB cutout`() {
        dashboardBarShape(isReady = true) shouldBe DashboardBottomBarShape
    }

    @Test
    fun `not-ready bar has no cutout - plain rounded top`() {
        val shape = dashboardBarShape(isReady = false)
        shape shouldNotBe DashboardBottomBarShape
        shape shouldBe RoundedCornerShape(
            topStart = DASHBOARD_CUTOUT_TOP_CORNER_RADIUS,
            topEnd = DASHBOARD_CUTOUT_TOP_CORNER_RADIUS,
        )
    }
}
