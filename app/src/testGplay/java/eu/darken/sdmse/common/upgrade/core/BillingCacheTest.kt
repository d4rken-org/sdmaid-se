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

        cache.stampLastProState(OurSku.Iap.PRO_UPGRADE.id, 1234L)

        cache.lastProStateAt.value() shouldBe 1234L
        cache.lastProStateSku.value() shouldBe OurSku.Iap.PRO_UPGRADE.id

        cache.stampLastProState(OurSku.Sub.PRO_UPGRADE.id, 5678L)

        cache.lastProStateAt.value() shouldBe 5678L
        cache.lastProStateSku.value() shouldBe OurSku.Sub.PRO_UPGRADE.id
    }
}
