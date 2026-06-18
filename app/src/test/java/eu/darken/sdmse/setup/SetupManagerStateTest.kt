package eu.darken.sdmse.setup

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class SetupManagerStateTest : BaseTest() {

    private fun loading(at: Instant = Instant.EPOCH) = object : SetupModule.State.Loading {
        override val startAt: Instant = at
        override val type: SetupModule.Type = SetupModule.Type.ROOT
    }

    private fun current(complete: Boolean) = object : SetupModule.State.Current {
        override val isComplete: Boolean = complete
        override val type: SetupModule.Type = SetupModule.Type.ROOT
    }

    private fun state(
        moduleStates: List<SetupModule.State>,
        isDismissed: Boolean = false,
        isHealerWorking: Boolean = false,
    ) = SetupManager.State(
        moduleStates = moduleStates,
        isDismissed = isDismissed,
        isHealerWorking = isHealerWorking,
    )

    @Test fun `incomplete module while another is still loading is not settled-incomplete`() {
        // Reproduces the dashboard flash: the root module reports a settled incomplete Result during
        // the cold-start handshake while other modules are still probing. isIncomplete is true, but
        // the card must NOT be shown immediately - so isIncompleteSettled must be false.
        val state = state(
            moduleStates = listOf(
                loading(),
                current(complete = false),
                current(complete = true),
            ),
        )

        state.isIncomplete shouldBe true
        state.isLoading shouldBe true
        state.isIncompleteSettled shouldBe false
        state.isDone shouldBe false
    }

    @Test fun `incomplete module once all modules have settled is settled-incomplete`() {
        val state = state(
            moduleStates = listOf(
                current(complete = true),
                current(complete = false),
                current(complete = true),
            ),
        )

        state.isIncomplete shouldBe true
        state.isLoading shouldBe false
        state.isIncompleteSettled shouldBe true
        state.isDone shouldBe false
    }

    @Test fun `all modules complete is done and not settled-incomplete`() {
        val state = state(
            moduleStates = listOf(
                current(complete = true),
                current(complete = true),
            ),
        )

        state.isIncomplete shouldBe false
        state.isLoading shouldBe false
        state.isIncompleteSettled shouldBe false
        state.isDone shouldBe true
    }

    @Test fun `healer working keeps an incomplete card visible even though setup has settled`() {
        // The healer running is a deliberately visible card state, so isIncompleteSettled stays true
        // when a settled module is incomplete and the healer is still working.
        val state = state(
            moduleStates = listOf(
                current(complete = true),
                current(complete = false),
            ),
            isHealerWorking = true,
        )

        state.isWorking shouldBe true
        state.isLoading shouldBe false
        state.isIncompleteSettled shouldBe true
        state.isDone shouldBe false
    }
}
