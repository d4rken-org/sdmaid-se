package eu.darken.sdmse.common.upgrade.core

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class UpgradeDiagnosticsGplayTest : BaseTest() {

    private fun create(snapshot: BillingCache.Snapshot) = UpgradeDiagnosticsGplay(
        billingCache = mockk<BillingCache>().apply { coEvery { this@apply.snapshot() } returns snapshot },
    )

    @Test
    fun `a never-pro install is reported as never, not as epoch zero`() = runTest {
        // The whole point of this line in the log header is telling "never bought" apart from
        // "bought once, entitlement now missing". A raw 0 reads as a 1970 timestamp.
        val info = create(
            BillingCache.Snapshot(lastProStateAt = 0L, lastProStateSku = "", proUnconfirmedSince = 0L)
        ).debugInfo()

        info shouldBe "BillingCache(lastProStateAt=never, lastProStateSku=unknown/legacy, proUnconfirmedSince=none)"
    }

    @Test
    fun `a confirmed purchase reports an instant and the sku`() = runTest {
        val info = create(
            BillingCache.Snapshot(
                lastProStateAt = 1_700_000_000_000L,
                lastProStateSku = OurSku.Iap.PRO_UPGRADE.id,
                proUnconfirmedSince = 0L,
            )
        ).debugInfo()

        info shouldContain "lastProStateAt=2023-11-14T22:13:20Z"
        info shouldContain "lastProStateSku=${OurSku.Iap.PRO_UPGRADE.id}"
        info shouldContain "proUnconfirmedSince=none"
    }

    @Test
    fun `an open unconfirmed episode is reported as an instant`() = runTest {
        val info = create(
            BillingCache.Snapshot(
                lastProStateAt = 1_700_000_000_000L,
                lastProStateSku = OurSku.Sub.PRO_UPGRADE.id,
                proUnconfirmedSince = 1_700_000_500_000L,
            )
        ).debugInfo()

        info shouldContain "proUnconfirmedSince=2023-11-14T22:21:40Z"
    }
}
