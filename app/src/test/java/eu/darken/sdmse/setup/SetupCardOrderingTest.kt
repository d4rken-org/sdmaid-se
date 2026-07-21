package eu.darken.sdmse.setup

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SetupCardOrderingTest : BaseTest() {

    @Test
    fun `card order stays canonical when completion changes`() {
        val inputOrder = listOf(
            SetupModule.Type.ROOT,
            SetupModule.Type.AUTOMATION,
            SetupModule.Type.INVENTORY,
            SetupModule.Type.SHIZUKU,
            SetupModule.Type.USAGE_STATS,
            SetupModule.Type.SAF,
            SetupModule.Type.NOTIFICATION,
            SetupModule.Type.STORAGE,
        )
        val expectedOrder = listOf(
            SetupModule.Type.INVENTORY,
            SetupModule.Type.NOTIFICATION,
            SetupModule.Type.STORAGE,
            SetupModule.Type.SAF,
            SetupModule.Type.SHIZUKU,
            SetupModule.Type.ROOT,
            SetupModule.Type.USAGE_STATS,
            SetupModule.Type.AUTOMATION,
        )

        val initial = inputOrder.mapIndexed { index, type -> item(type, complete = index % 2 == 0) }
        val updated = inputOrder.mapIndexed { index, type -> item(type, complete = index % 2 != 0) }

        initial.sortedInSetupDisplayOrder().map { it.state.type } shouldBe expectedOrder
        updated.sortedInSetupDisplayOrder().map { it.state.type } shouldBe expectedOrder
    }

    private fun item(moduleType: SetupModule.Type, complete: Boolean): SetupCardItem = TestItem(
        state = object : SetupModule.State.Current {
            override val type = moduleType
            override val isComplete = complete
        },
    )

    private data class TestItem(
        override val state: SetupModule.State,
    ) : SetupCardItem
}
