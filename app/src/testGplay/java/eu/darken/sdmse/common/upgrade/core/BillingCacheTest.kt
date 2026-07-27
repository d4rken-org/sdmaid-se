package eu.darken.sdmse.common.upgrade.core

import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.datastore.value
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class BillingCacheTest : BaseTest() {

    // One test method on purpose: BillingCache is a @Singleton in production, and DataStore
    // forbids two active instances on the same file — a second BillingCache in this process
    // would crash, not exercise anything real.
    @Test
    fun `stampLastProState round-trips through the DataStoreValues`() = runTest {
        // Real DataStore, no mocks: this catches an encoding mismatch between the raw keys the
        // atomic stamp transaction writes and the keys/types the DataStoreValues read.
        val cache = BillingCache(ApplicationProvider.getApplicationContext())

        cache.lastProStateAt.value() shouldBe 0L
        cache.lastProStateSku.value() shouldBe ""

        // Defaults on a never-Pro install: this exact triple is what the debug-log header reports
        // as "never / unknown-legacy / none", and it's the signal that separates a never-bought
        // install from one whose entitlement went missing.
        cache.snapshot() shouldBe BillingCache.Snapshot(
            lastProStateAt = 0L,
            lastProStateSku = "",
            proUnconfirmedSince = 0L,
        )

        cache.stampLastProState(OurSku.Iap.PRO_UPGRADE.id, 1234L)

        cache.lastProStateAt.value() shouldBe 1234L
        cache.lastProStateSku.value() shouldBe OurSku.Iap.PRO_UPGRADE.id

        cache.stampLastProState(OurSku.Sub.PRO_UPGRADE.id, 5678L)

        cache.lastProStateAt.value() shouldBe 5678L
        cache.lastProStateSku.value() shouldBe OurSku.Sub.PRO_UPGRADE.id

        // Occurrence-aware episode clear: a confirmation closes an episode that began at or before
        // it, but must leave a NEWER episode intact — a connection failure that occurred after this
        // confirmation but was processed out of order opened a still-valid episode.
        cache.proUnconfirmedSince.value(4_000L)
        cache.stampLastProState(OurSku.Iap.PRO_UPGRADE.id, 5_000L) // confirmation newer than episode
        cache.proUnconfirmedSince.value() shouldBe 0L

        cache.proUnconfirmedSince.value(9_000L)
        cache.stampLastProState(OurSku.Iap.PRO_UPGRADE.id, 8_000L) // confirmation older than episode
        cache.proUnconfirmedSince.value() shouldBe 9_000L

        // snapshot() must agree with the individual reads. It exists so the debug-log header reads
        // all three in ONE DataStore emission: three separate reads can straddle a concurrent
        // stampLastProState and report a combination that never existed.
        cache.snapshot() shouldBe BillingCache.Snapshot(
            lastProStateAt = 8_000L,
            lastProStateSku = OurSku.Iap.PRO_UPGRADE.id,
            proUnconfirmedSince = 9_000L,
        )
    }
}
