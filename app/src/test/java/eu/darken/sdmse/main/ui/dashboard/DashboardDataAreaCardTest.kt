package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DashboardDataAreaCardTest : BaseTest() {

    private val area = DataArea(
        path = LocalPath.build("storage", "emulated", "0"),
        type = DataArea.Type.SDCARD,
        userHandle = UserHandle2(0),
    )

    private fun cardFor(result: Result<DataAreaManager.State>?) = DashboardViewModel.dataAreaCardFor(result) {}

    @Test
    fun `no card before any build`() {
        cardFor(null) shouldBe null
    }

    @Test
    fun `no card when areas were found`() {
        cardFor(Result.success(DataAreaManager.State(setOf(area)))) shouldBe null
    }

    @Test
    fun `an empty build shows the card as empty`() {
        cardFor(Result.success(DataAreaManager.State(emptySet())))!!.buildFailed shouldBe false
    }

    @Test
    fun `a failed build shows the card as failed`() {
        cardFor(Result.failure(IllegalStateException("build failed")))!!.buildFailed shouldBe true
    }
}
