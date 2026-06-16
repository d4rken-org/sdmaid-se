package eu.darken.sdmse.main.ui.onboarding.setup

import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.navigation.NavigationController
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.setup.SetupScreenOptions
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

@ExtendWith(MockKExtension::class)
class OnboardingSetupViewModelTest : BaseTest() {

    @MockK lateinit var generalSettings: GeneralSettings
    @MockK(relaxed = true) lateinit var navController: NavigationController

    private lateinit var onboardingFlow: MutableStateFlow<Boolean>
    private lateinit var toursFlow: MutableStateFlow<Boolean>

    @BeforeEach
    fun setup() {
        onboardingFlow = MutableStateFlow(false)
        toursFlow = MutableStateFlow(true)
        every { generalSettings.isOnboardingCompleted } returns boolValue(onboardingFlow)
        every { generalSettings.isGuidedToursEnabled } returns boolValue(toursFlow)
    }

    private fun boolValue(backing: MutableStateFlow<Boolean>): DataStoreValue<Boolean> = mockk {
        every { flow } returns backing
        coEvery { update(any<(Boolean) -> Boolean?>()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val fn = firstArg<(Boolean) -> Boolean?>()
            val old = backing.value
            val new = fn(old) ?: old
            backing.value = new
            DataStoreValue.Updated(old, new)
        }
    }

    private fun buildVM() = OnboardingSetupViewModel(
        handle = SavedStateHandle(),
        dispatcherProvider = TestDispatcherProvider(),
        generalSettings = generalSettings,
        navController = navController,
    )

    @Test
    fun `toggling does not persist until finishOnboarding, then writes the final choice`() = runTest2 {
        toursFlow.value = true
        val vm = buildVM()

        // Multiple toggles must NOT touch the store — persistence is single-sourced at finish, so a
        // stale async write can never land after the final value (the race this design prevents).
        vm.onGuidedToursChanged(false)
        vm.onGuidedToursChanged(true)
        vm.onGuidedToursChanged(false)
        toursFlow.first() shouldBe true // unchanged: nothing persisted yet

        vm.finishOnboarding()

        // Only the final choice is persisted — blocking, before navigation — so the setup tour can't
        // start with a stale value.
        toursFlow.first() shouldBe false
        onboardingFlow.first() shouldBe true
        verify { navController.goTo(SetupRoute(options = SetupScreenOptions(isOnboarding = true))) }
    }

    @Test
    fun `finishOnboarding persists 'enabled' when the toggle is left on`() = runTest2 {
        toursFlow.value = true
        val vm = buildVM()

        vm.finishOnboarding()

        toursFlow.first() shouldBe true
        onboardingFlow.first() shouldBe true
    }
}
