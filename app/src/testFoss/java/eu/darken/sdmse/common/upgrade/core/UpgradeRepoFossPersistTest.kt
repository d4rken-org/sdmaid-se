package eu.darken.sdmse.common.upgrade.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.serialization.SerializationAppModule
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class UpgradeRepoFossPersistTest : BaseTest() {

    // One test method on purpose: FossCache is a @Singleton in production, and DataStore forbids
    // two active instances on the same file — a second FossCache on `settings_foss` in this
    // process would crash, not exercise anything real.
    @Test
    fun `persistUpgrade is create-only-if-absent`() = runTest {
        // backgroundScope belongs to the test scope, capture it before switching dispatchers.
        val repoScope = backgroundScope
        // Real time on purpose: the real DataStore does its I/O off the test scheduler, so virtual
        // time would let the assertions run past writes that haven't happened yet.
        withContext(Dispatchers.IO) {
            // Real DataStore, no mocks: the point is the transaction inside the store, a mocked
            // DataStoreValue would only replay whatever this test told it to.
            val cache = FossCache(ApplicationProvider.getApplicationContext<Context>(), SerializationAppModule().json())
            val repo = UpgradeRepoFoss(
                appScope = repoScope,
                fossCache = cache,
                webpageTool = mockk(),
            )

            cache.upgrade.value() shouldBe null

            // An existing supporter: this record and its date are what the status screen shows.
            cache.upgrade.value(
                FossUpgrade(upgradedAt = Instant.EPOCH, upgradeType = FossUpgrade.Type.GITHUB_SPONSORS)
            )

            // The regression: a persist call on an existing record must keep it, not overwrite it
            // with a fresh "supporter since" timestamp.
            repo.persistUpgrade() shouldBe false
            cache.upgrade.value() shouldBe FossUpgrade(
                upgradedAt = Instant.EPOCH,
                upgradeType = FossUpgrade.Type.GITHUB_SPONSORS,
            )

            repo.upgradeInfo.first().apply {
                isPro shouldBe true
                upgradedAt shouldBe Instant.EPOCH
            }

            cache.upgrade.value(null)

            // Create path: a fresh unlock stamps "now", bracketed so the assertion doesn't depend
            // on a fixed clock.
            val before = Instant.now()
            repo.persistUpgrade() shouldBe true
            val after = Instant.now()

            val created = cache.upgrade.value()!!
            created.upgradeType shouldBe FossUpgrade.Type.GITHUB_SPONSORS
            (created.upgradedAt >= before && created.upgradedAt <= after) shouldBe true

            // Keep-branch proof via the returned Boolean: immune to a timestamp collision making
            // the record comparison vacuously true.
            repo.persistUpgrade() shouldBe false
            cache.upgrade.value() shouldBe created
        }
    }
}
