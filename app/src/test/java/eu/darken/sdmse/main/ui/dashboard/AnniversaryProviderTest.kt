package eu.darken.sdmse.main.ui.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.main.core.CurriculumVitae
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.stats.core.StatsRepo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.runTest2
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AnniversaryProviderTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val curriculumVitae = mockk<CurriculumVitae>()
    private val generalSettings = mockk<GeneralSettings>()
    private val upgradeRepo = mockk<UpgradeRepo>()
    private val statsRepo = mockk<StatsRepo>()
    private val dismissedSetting = mockk<DataStoreValue<Int?>>(relaxed = true)

    @After fun teardown() {
        unmockkAll()
    }

    private val zone: ZoneId = ZoneOffset.UTC

    private fun instantOf(date: LocalDate): Instant = date.atStartOfDay(zone).toInstant()

    private fun upgradeInfo(isPro: Boolean) = mockk<UpgradeRepo.Info>().apply {
        every { this@apply.isPro } returns isPro
    }

    private fun stub(dismissedOrdinal: Int?, installedAt: Instant, isPro: Boolean = true) {
        every { dismissedSetting.flow } returns flowOf(dismissedOrdinal)
        every { generalSettings.anniversaryDismissedOrdinal } returns dismissedSetting
        every { curriculumVitae.installedAt } returns flowOf(installedAt)
        every { upgradeRepo.upgradeInfo } returns flowOf(upgradeInfo(isPro))
        every { upgradeRepo.storeSite } returns "https://example.test"
        every { statsRepo.state } returns flowOf(
            StatsRepo.State(
                reportsCount = 1,
                snapshotsCount = 1,
                totalSpaceFreed = 1024L,
                itemsProcessed = 7L,
                databaseSize = 128L,
            ),
        )
    }

    private fun TestScope.provider() = AnniversaryProvider(
        appScope = this,
        context = context,
        curriculumVitae = curriculumVitae,
        generalSettings = generalSettings,
        upgradeRepo = upgradeRepo,
        statsRepo = statsRepo,
    )

    @Test fun `dismissing a December window in January stores the occurrence ordinal`() = runTest2 {
        // The dismissal must not be keyed on the calendar year: today is 2027 but the celebration
        // running is still the 2026 (second) anniversary of a 2024-12-20 install.
        val installedAt = instantOf(LocalDate.of(2024, 12, 20))
        stub(dismissedOrdinal = null, installedAt = installedAt)

        val item = provider().buildItem(
            dismissedOrdinal = null,
            installedAt = installedAt,
            upgradeInfo = upgradeInfo(true),
            zone = zone,
            today = LocalDate.of(2027, 1, 2),
        )

        item.shouldNotBeNull()
        item.years shouldBe 2

        item.onDismiss()
        advanceUntilIdle()

        val update = slot<(Int?) -> Int?>()
        coVerify { dismissedSetting.update(capture(update)) }
        update.captured.invoke(null) shouldBe 2
    }

    @Test fun `a dismissed occurrence stays hidden`() = runTest2 {
        val installedAt = instantOf(LocalDate.of(2024, 12, 20))
        stub(dismissedOrdinal = 2, installedAt = installedAt)

        provider().buildItem(
            dismissedOrdinal = 2,
            installedAt = installedAt,
            upgradeInfo = upgradeInfo(true),
            zone = zone,
            today = LocalDate.of(2027, 1, 2),
        ).shouldBeNull()
    }

    @Test fun `non-pro users never see the card`() = runTest2 {
        val installedAt = instantOf(LocalDate.of(2024, 12, 20))
        stub(dismissedOrdinal = null, installedAt = installedAt, isPro = false)

        provider().buildItem(
            dismissedOrdinal = null,
            installedAt = installedAt,
            upgradeInfo = upgradeInfo(false),
            zone = zone,
            today = LocalDate.of(2027, 1, 2),
        ).shouldBeNull()
    }

    @Test fun `the next anniversary is shown again after a dismissal`() = runTest2 {
        val installedAt = instantOf(LocalDate.of(2024, 12, 20))
        stub(dismissedOrdinal = 2, installedAt = installedAt)

        val item = provider().buildItem(
            dismissedOrdinal = 2,
            installedAt = installedAt,
            upgradeInfo = upgradeInfo(true),
            zone = zone,
            today = LocalDate.of(2027, 12, 25),
        )

        item.shouldNotBeNull()
        item.years shouldBe 3
    }

    @Test fun `the item flow reads the dismissal ordinal setting`() = runTest2 {
        val installedAt = LocalDate.now(ZoneId.systemDefault())
            .minusYears(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
        stub(dismissedOrdinal = null, installedAt = installedAt)

        provider().item.first().shouldNotBeNull()
    }
}
