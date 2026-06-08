package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.exclusion.core.types.DefaultExclusion
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class ExclusionManagerTest : BaseTest() {

    private val defaultPkg = "com.default.app".toPkgId()
    private val defaultExclusion = PkgExclusion(defaultPkg, setOf(Exclusion.Tag.APPCLEANER))
    private val defaultId = defaultExclusion.id
    private val defaultHolder = DefaultExclusion("https://example.com/reason", defaultExclusion)

    private fun defaults() = mockk<DefaultExclusions>(relaxed = true).apply {
        every { defaultIds } returns setOf(defaultId)
        every { exclusions } returns flowOf(listOf(defaultHolder))
    }

    private fun create(
        scope: CoroutineScope,
        initialUser: Set<Exclusion>,
        defaults: DefaultExclusions,
    ): Pair<ExclusionManager, ExclusionStorage> {
        val storage = mockk<ExclusionStorage>(relaxed = true).apply {
            coEvery { load() } returns initialUser
        }
        val manager = ExclusionManager(
            appScope = scope,
            dispatcherProvider = TestDispatcherProvider(),
            exclusionStorage = storage,
            defaultExclusions = defaults,
        )
        return manager to storage
    }

    @Test
    fun `restoreDefaults drops shadowing user exclusions and resets defaults`() = runTest2 {
        // A user exclusion shadowing the default (same ID, different tags) plus an unrelated one.
        val shadow = PkgExclusion(defaultPkg, setOf(Exclusion.Tag.GENERAL))
        val other = PkgExclusion("com.other.app".toPkgId(), setOf(Exclusion.Tag.GENERAL))
        val defaults = defaults()
        val (manager, storage) = create(backgroundScope, setOf(shadow, other), defaults)

        manager.restoreDefaults()
        advanceUntilIdle()

        val saved = slot<Set<Exclusion>>()
        coVerify { storage.save(capture(saved)) }
        saved.captured shouldContainExactly setOf(other)
        coVerify(exactly = 1) { defaults.reset() }
    }

    @Test
    fun `restoreDefaults without shadows only resets defaults`() = runTest2 {
        val other = PkgExclusion("com.other.app".toPkgId(), setOf(Exclusion.Tag.GENERAL))
        val defaults = defaults()
        val (manager, storage) = create(backgroundScope, setOf(other), defaults)

        manager.restoreDefaults()
        advanceUntilIdle()

        // No user storage write when nothing shadows a default.
        coVerify(exactly = 0) { storage.save(any()) }
        coVerify(exactly = 1) { defaults.reset() }
    }

    @Test
    fun `removing a user exclusion does not pollute the removed-defaults set`() = runTest2 {
        val user = PkgExclusion("com.other.app".toPkgId(), setOf(Exclusion.Tag.GENERAL))
        val defaults = defaults()
        val (manager, storage) = create(backgroundScope, setOf(user), defaults)

        manager.remove(setOf(user.id))
        advanceUntilIdle()

        val saved = slot<Set<Exclusion>>()
        coVerify { storage.save(capture(saved)) }
        saved.captured shouldContainExactly emptySet()
        // Crucial: a user-only removal must not forward anything to the defaults store.
        coVerify(exactly = 0) { defaults.remove(any()) }
    }

    @Test
    fun `removing a default ID is forwarded to the defaults store`() = runTest2 {
        val defaults = defaults()
        val (manager, _) = create(backgroundScope, emptySet(), defaults)

        manager.remove(setOf(defaultId))
        advanceUntilIdle()

        coVerify(exactly = 1) { defaults.remove(setOf(defaultId)) }
    }
}
