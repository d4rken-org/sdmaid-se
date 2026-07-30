package eu.darken.sdmse.common.upgrade.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.datastore.value
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class BillingCacheTest : BaseTest() {

    // One test method on purpose: BillingCache is a @Singleton in production, and DataStore
    // forbids two active instances on the same file — a second BillingCache in this process
    // would crash, not exercise anything real.
    @Test
    fun `stampLastProState round-trips through the DataStoreValues`() = runTest {
        // Real time on purpose: the reads/writes below are bounded by cacheTimeoutMs, and the real
        // DataStore does its I/O off the test scheduler -- under virtual time the bound would fire
        // instantly while nothing else is scheduled.
        withContext(Dispatchers.IO) {
            // Real DataStore, no mocks: this catches an encoding mismatch between the raw keys the
            // atomic stamp transaction writes and the keys/types the DataStoreValues read.
            val cache = BillingCache(ApplicationProvider.getApplicationContext<Context>())

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

    @Test
    fun `a wedged datastore is bounded, reads fail loudly and writes fail soft`() = runTest {
        // Fake store, no file: a second real DataStore on the same file would crash this process.
        val cache = BillingCache(HangingPreferencesDataStore()).apply { cacheTimeoutMs = 50L }

        // A timeout must not masquerade as a default snapshot -- "never bought" and "couldn't read
        // the evidence" are the two things the debug-log header exists to tell apart.
        shouldThrow<IOException> { cache.snapshot() }

        // The write only decorates the entitlement path, it must never block it.
        cache.stampLastProState(OurSku.Iap.PRO_UPGRADE.id, 1234L)
    }

    @Test
    fun `a failing datastore write does not abort the entitlement bookkeeping`() = runTest {
        // A broken write (corrupt file, no disk space) is not the same failure as a wedged lock, and
        // the timeout alone doesn't cover it -- the exception would propagate straight through the
        // stamp into the caller that was only decorating its entitlement work.
        val cache = BillingCache(ThrowingPreferencesDataStore())

        cache.stampLastProState(OurSku.Iap.PRO_UPGRADE.id, 1234L)

        // Reads stay loud: a snapshot that can't be read must not look like "never bought".
        shouldThrow<IOException> { cache.snapshot() }
    }

    @Test
    fun `a cancelled stamp still cancels`() = runTest {
        // Fail-soft must not extend to cancellation -- swallowing it would break the structured
        // concurrency of whatever entitlement work is being torn down.
        val cache = BillingCache(CancellingPreferencesDataStore())

        shouldThrow<CancellationException> { cache.stampLastProState(OurSku.Iap.PRO_UPGRADE.id, 1234L) }
    }
}

/** DataStore that never answers -- stands in for a wedged file lock. */
internal class HangingPreferencesDataStore : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { awaitCancellation() }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        awaitCancellation()
}

/** DataStore whose I/O fails -- stands in for a corrupt file or a full disk. */
internal class ThrowingPreferencesDataStore : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw IOException("Preferences file is broken") }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        throw IOException("Preferences file is broken")
}

/** DataStore whose write is cancelled from the outside. */
internal class CancellingPreferencesDataStore : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw CancellationException("Torn down") }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        throw CancellationException("Torn down")
}
