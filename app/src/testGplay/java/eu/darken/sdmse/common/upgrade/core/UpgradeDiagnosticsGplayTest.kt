package eu.darken.sdmse.common.upgrade.core

import eu.darken.sdmse.main.core.CurriculumVitae
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class UpgradeDiagnosticsGplayTest : BaseTest() {

    private val proHistory = CurriculumVitae.ProHistory(
        lastState = CurriculumVitae.ProState.PURCHASED,
        graceEngagedCount = 2,
        graceEngagedLast = null,
        proLostCount = 0,
        proLostLast = null,
    )

    private fun create(
        snapshot: () -> BillingCache.Snapshot,
        history: () -> CurriculumVitae.ProHistory = { proHistory },
    ) = UpgradeDiagnosticsGplay(
        billingCache = mockk<BillingCache>().apply { coEvery { this@apply.snapshot() } answers { snapshot() } },
        curriculumVitae = mockk<CurriculumVitae>().apply { coEvery { proHistory() } answers { history() } },
    )

    @Test
    fun `a never-pro install is reported as never, not as epoch zero`() = runTest {
        // The whole point of this line in the log header is telling "never bought" apart from
        // "bought once, entitlement now missing". A raw 0 reads as a 1970 timestamp.
        val info = create(
            { BillingCache.Snapshot(lastProStateAt = 0L, lastProStateSku = "", proUnconfirmedSince = 0L) }
        ).debugInfo()

        info shouldContain
            "BillingCache(lastProStateAt=never, lastProStateSku=unknown/legacy, proUnconfirmedSince=none)"
    }

    @Test
    fun `the pro history rides along so a complaint arrives with both records`() = runTest {
        val info = create(
            { BillingCache.Snapshot(lastProStateAt = 0L, lastProStateSku = "", proUnconfirmedSince = 0L) }
        ).debugInfo()

        info shouldContain "ProHistory=$proHistory"
    }

    @Test
    fun `a broken history read still reports the billing cache`() = runTest {
        // Different DataStores: one failing must not suppress the other's independent evidence.
        val info = create(
            snapshot = {
                BillingCache.Snapshot(
                    lastProStateAt = 1_700_000_000_000L,
                    lastProStateSku = OurSku.Iap.PRO_UPGRADE.id,
                    proUnconfirmedSince = 0L,
                )
            },
            history = { throw IllegalStateException("storage is full") },
        ).debugInfo()

        info shouldContain "lastProStateSku=${OurSku.Iap.PRO_UPGRADE.id}"
        info shouldContain "ProHistory=unavailable"
    }

    @Test
    fun `a broken billing cache read still reports the pro history`() = runTest {
        // Mirror image of the above: the billing cache DataStore failing must not suppress the
        // lifetime pro-state counters, which live in a different store.
        var historyReads = 0
        val info = create(
            snapshot = { throw IllegalStateException("datastore is corrupt") },
            history = { historyReads++; proHistory },
        ).debugInfo()

        historyReads shouldBe 1
        info shouldContain "BillingCache=unavailable"
        info shouldContain "ProHistory=$proHistory"
    }

    @Test
    fun `a cancelled billing cache read is not swallowed`() = runTest {
        shouldThrow<CancellationException> {
            create(
                snapshot = { throw CancellationException("scope died") },
            ).debugInfo()
        }
    }

    @Test
    fun `a cancelled history read is not swallowed`() = runTest {
        // Symmetric to the cache read: cancellation is not a diagnostics failure, it means the
        // caller's scope died and the header read must unwind with it.
        shouldThrow<CancellationException> {
            create(
                snapshot = {
                    BillingCache.Snapshot(lastProStateAt = 0L, lastProStateSku = "", proUnconfirmedSince = 0L)
                },
                history = { throw CancellationException("scope died") },
            ).debugInfo()
        }
    }

    @Test
    fun `a wedged billing cache is reported as unavailable, not as a never-pro install`() = runTest {
        // End-to-end over a real BillingCache whose store never answers: the bounded read throws,
        // and the header must say the evidence is missing instead of claiming "never bought".
        val diagnostics = UpgradeDiagnosticsGplay(
            billingCache = BillingCache(HangingPreferencesDataStore()).apply { cacheTimeoutMs = 50L },
            curriculumVitae = mockk<CurriculumVitae>().apply { coEvery { proHistory() } returns proHistory },
        )

        val info = diagnostics.debugInfo()

        info shouldContain "BillingCache=unavailable"
        info shouldNotContain "lastProStateAt=never"
        info shouldContain "ProHistory=$proHistory"
    }

    @Test
    fun `a wedged history is reported as unavailable`() = runTest {
        // Counterpart to the wedged cache above: a never-answering CurriculumVitae store would hold
        // the debug-log header -- and with it the start of the recording -- forever.
        val diagnostics = UpgradeDiagnosticsGplay(
            billingCache = mockk<BillingCache>().apply {
                coEvery { snapshot() } returns BillingCache.Snapshot(
                    lastProStateAt = 0L,
                    lastProStateSku = "",
                    proUnconfirmedSince = 0L,
                )
            },
            curriculumVitae = mockk<CurriculumVitae>().apply {
                coEvery { proHistory() } coAnswers { awaitCancellation() }
            },
        ).apply { historyTimeoutMs = 50L }

        val info = diagnostics.debugInfo()

        info shouldContain "BillingCache(lastProStateAt=never"
        info shouldContain "ProHistory=unavailable"
    }

    @Test
    fun `a confirmed purchase reports an instant and the sku`() = runTest {
        val info = create(
            {
                BillingCache.Snapshot(
                    lastProStateAt = 1_700_000_000_000L,
                    lastProStateSku = OurSku.Iap.PRO_UPGRADE.id,
                    proUnconfirmedSince = 0L,
                )
            }
        ).debugInfo()

        info shouldContain "lastProStateAt=2023-11-14T22:13:20Z"
        info shouldContain "lastProStateSku=${OurSku.Iap.PRO_UPGRADE.id}"
        info shouldContain "proUnconfirmedSince=none"
    }

    @Test
    fun `an open unconfirmed episode is reported as an instant`() = runTest {
        val info = create(
            {
                BillingCache.Snapshot(
                    lastProStateAt = 1_700_000_000_000L,
                    lastProStateSku = OurSku.Sub.PRO_UPGRADE.id,
                    proUnconfirmedSince = 1_700_000_500_000L,
                )
            }
        ).debugInfo()

        info shouldContain "proUnconfirmedSince=2023-11-14T22:21:40Z"
    }
}
