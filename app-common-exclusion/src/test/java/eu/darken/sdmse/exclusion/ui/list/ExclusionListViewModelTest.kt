package eu.darken.sdmse.exclusion.ui.list

import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.exclusion.core.DefaultExclusions
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.DefaultExclusion
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionHolder
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.exclusion.core.types.UserExclusion
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class ExclusionListViewModelTest : BaseTest() {

    private val defaultPkg = "com.default.app".toPkgId()
    private val defaultExclusion = PkgExclusion(defaultPkg, setOf(Exclusion.Tag.APPCLEANER))
    private val defaultId = defaultExclusion.id

    // safeStateIn uses WhileSubscribed; keep a subscriber alive via backgroundScope so the upstream
    // combine runs and state reflects the mocked holders instead of the State() initial value.
    private fun TestScope.harness(holders: Collection<ExclusionHolder>): ExclusionListViewModel {
        val exclusionManager = mockk<ExclusionManager>(relaxed = true).apply {
            every { exclusions } returns flowOf(holders)
        }
        val pkgRepo = mockk<PkgRepo>(relaxed = true).apply {
            every { data } returns emptyFlow()
        }
        val defaultExclusions = mockk<DefaultExclusions>(relaxed = true).apply {
            every { defaultIds } returns setOf(defaultId)
        }
        val vm = ExclusionListViewModel(
            handle = SavedStateHandle(),
            dispatcherProvider = TestDispatcherProvider(),
            context = mockk(relaxed = true),
            exclusionManager = exclusionManager,
            pkgRepo = pkgRepo,
            gatewaySwitch = mockk(relaxed = true),
            defaultExclusions = defaultExclusions,
            webpageTool = mockk(relaxed = true),
            upgradeRepo = mockk(relaxed = true),
            legacyImporter = mockk(relaxed = true),
            exclusionImporter = mockk(relaxed = true),
        )
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            vm.state.collect { }
        }
        return vm
    }

    @Test
    fun `defaultsModified is false when the built-in default is effective`() = runTest2 {
        val vm = harness(listOf(DefaultExclusion("https://example.com/reason", defaultExclusion)))
        advanceUntilIdle()
        vm.state.first().defaultsModified shouldBe false
    }

    @Test
    fun `defaultsModified is true when the default was removed`() = runTest2 {
        val vm = harness(emptyList())
        advanceUntilIdle()
        vm.state.first().defaultsModified shouldBe true
    }

    @Test
    fun `defaultsModified is true when a user exclusion shadows the default`() = runTest2 {
        // Same ID as the default (IDs ignore tags), but it's a user exclusion, so the default is
        // no longer effective even though "showDefaults" is off.
        val shadow = UserExclusion(PkgExclusion(defaultPkg, setOf(Exclusion.Tag.GENERAL)))
        val vm = harness(listOf(shadow))
        advanceUntilIdle()
        vm.state.first().defaultsModified shouldBe true
    }
}
