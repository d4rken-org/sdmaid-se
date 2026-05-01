package eu.darken.sdmse.main.ui.settings.cards

import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.main.core.DashboardCardConfig
import eu.darken.sdmse.main.core.DashboardCardType
import eu.darken.sdmse.main.core.GeneralSettings
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

@ExtendWith(MockKExtension::class)
class DashboardCardConfigViewModelTest : BaseTest() {

    @MockK lateinit var generalSettings: GeneralSettings

    private lateinit var backingFlow: MutableStateFlow<DashboardCardConfig>
    private lateinit var fakeValue: DataStoreValue<DashboardCardConfig>

    @BeforeEach
    fun setup() {
        backingFlow = MutableStateFlow(DashboardCardConfig())
        fakeValue = io.mockk.mockk {
            every { flow } returns backingFlow
            coEvery { update(any<(DashboardCardConfig) -> DashboardCardConfig?>()) } coAnswers {
                @Suppress("UNCHECKED_CAST")
                val fn = firstArg<(DashboardCardConfig) -> DashboardCardConfig?>()
                val old = backingFlow.value
                val new = fn(old) ?: old
                backingFlow.value = new
                DataStoreValue.Updated(old, new)
            }
        }
        every { generalSettings.dashboardCardConfig } returns fakeValue
    }

    private fun buildVM() = DashboardCardConfigViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        generalSettings = generalSettings,
    )

    @Test
    fun `applyReorder preserves isVisible flags`() = runTest2 {
        backingFlow.value = DashboardCardConfig(
            cards = listOf(
                DashboardCardConfig.CardEntry(DashboardCardType.CORPSEFINDER, isVisible = true),
                DashboardCardConfig.CardEntry(DashboardCardType.APPCLEANER, isVisible = false),
                DashboardCardConfig.CardEntry(DashboardCardType.SYSTEMCLEANER, isVisible = true),
                DashboardCardConfig.CardEntry(DashboardCardType.DEDUPLICATOR, isVisible = true),
                DashboardCardConfig.CardEntry(DashboardCardType.APPCONTROL, isVisible = true),
                DashboardCardConfig.CardEntry(DashboardCardType.ANALYZER, isVisible = true),
                DashboardCardConfig.CardEntry(DashboardCardType.SWIPER, isVisible = true),
                DashboardCardConfig.CardEntry(DashboardCardType.SQUEEZER, isVisible = true),
                DashboardCardConfig.CardEntry(DashboardCardType.SCHEDULER, isVisible = true),
                DashboardCardConfig.CardEntry(DashboardCardType.STATS, isVisible = true),
            ),
        )

        val vm = buildVM()
        vm.applyReorder(
            listOf(
                DashboardCardType.APPCLEANER,
                DashboardCardType.CORPSEFINDER,
                DashboardCardType.SYSTEMCLEANER,
                DashboardCardType.DEDUPLICATOR,
                DashboardCardType.APPCONTROL,
                DashboardCardType.ANALYZER,
                DashboardCardType.SWIPER,
                DashboardCardType.SQUEEZER,
                DashboardCardType.SCHEDULER,
                DashboardCardType.STATS,
            ),
        )

        val result = backingFlow.first()
        result.cards.first { it.type == DashboardCardType.APPCLEANER }.isVisible shouldBe false
        result.cards.first { it.type == DashboardCardType.CORPSEFINDER }.isVisible shouldBe true
        result.cards.map { it.type }.first() shouldBe DashboardCardType.APPCLEANER
    }

    @Test
    fun `applyReorder ignores partial snapshot`() = runTest2 {
        val original = DashboardCardConfig()
        backingFlow.value = original

        val vm = buildVM()
        vm.applyReorder(listOf(DashboardCardType.CORPSEFINDER, DashboardCardType.APPCLEANER))

        backingFlow.first() shouldBe original
    }

    @Test
    fun `applyReorder is noop when order matches`() = runTest2 {
        val original = DashboardCardConfig()
        backingFlow.value = original

        val vm = buildVM()
        vm.applyReorder(original.cards.map { it.type })

        backingFlow.first() shouldBe original
    }

    @Test
    fun `toggleVisibility flips single entry only`() = runTest2 {
        backingFlow.value = DashboardCardConfig()

        val vm = buildVM()
        vm.toggleVisibility(DashboardCardType.ANALYZER)

        val result = backingFlow.first()
        result.cards.first { it.type == DashboardCardType.ANALYZER }.isVisible shouldBe false
        result.cards
            .filter { it.type != DashboardCardType.ANALYZER }
            .all { it.isVisible } shouldBe true
    }

    @Test
    fun `resetToDefaults restores factory config`() = runTest2 {
        backingFlow.value = DashboardCardConfig(
            cards = listOf(
                DashboardCardConfig.CardEntry(DashboardCardType.SCHEDULER, isVisible = false),
            ),
        )

        val vm = buildVM()
        vm.resetToDefaults()

        backingFlow.first() shouldBe DashboardCardConfig()
    }
}
