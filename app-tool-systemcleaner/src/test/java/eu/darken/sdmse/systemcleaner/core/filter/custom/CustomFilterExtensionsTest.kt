package eu.darken.sdmse.systemcleaner.core.filter.custom

import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.rwDataStoreValue
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class CustomFilterExtensionsTest : BaseTest() {

    private class Harness(
        val settings: SystemCleanerSettings,
        val enabledValue: eu.darken.sdmse.common.datastore.DataStoreValue<Set<String>>,
    )

    private fun harness(initial: Set<String> = emptySet()): Harness {
        val enabledValue = rwDataStoreValue(initial)
        val settings = mockk<SystemCleanerSettings>().apply {
            every { enabledCustomFilter } returns enabledValue
        }
        return Harness(settings, enabledValue)
    }

    @Test
    fun `toggleCustomFilter with enabled true adds the id when absent`() = runTest2 {
        val h = harness(initial = emptySet())
        val updater = slot<(Set<String>) -> Set<String>?>()
        io.mockk.coEvery {
            h.enabledValue.update(capture(updater))
        } returns eu.darken.sdmse.common.datastore.DataStoreValue.Updated(old = emptySet(), new = setOf("x"))

        h.settings.toggleCustomFilter("x", enabled = true)

        // The lambda's behavior is "add when absent OR enabled==false → remove. Otherwise add."
        // For `enabled=true` and absent id, the lambda must produce a set containing the id.
        updater.captured(emptySet()) shouldBe setOf("x")
    }

    @Test
    fun `toggleCustomFilter with enabled false always removes the id`() = runTest2 {
        // The actual code: `if (it.contains(filterId) || enabled == false) it - filterId else it + filterId`
        // So enabled=false → unconditionally removes (or no-op if absent).
        val h = harness(initial = setOf("present"))
        val updater = slot<(Set<String>) -> Set<String>?>()
        io.mockk.coEvery { h.enabledValue.update(capture(updater)) } returns
            eu.darken.sdmse.common.datastore.DataStoreValue.Updated(old = setOf("present"), new = emptySet())

        h.settings.toggleCustomFilter("present", enabled = false)

        updater.captured(setOf("present")) shouldBe emptySet()
    }

    @Test
    fun `toggleCustomFilter with enabled null toggles - removes when present`() = runTest2 {
        val h = harness(initial = setOf("toggle"))
        val updater = slot<(Set<String>) -> Set<String>?>()
        io.mockk.coEvery { h.enabledValue.update(capture(updater)) } returns
            eu.darken.sdmse.common.datastore.DataStoreValue.Updated(old = setOf("toggle"), new = emptySet())

        h.settings.toggleCustomFilter("toggle", enabled = null)

        updater.captured(setOf("toggle")) shouldBe emptySet()
    }

    @Test
    fun `toggleCustomFilter with enabled null toggles - adds when absent`() = runTest2 {
        val h = harness(initial = emptySet())
        val updater = slot<(Set<String>) -> Set<String>?>()
        io.mockk.coEvery { h.enabledValue.update(capture(updater)) } returns
            eu.darken.sdmse.common.datastore.DataStoreValue.Updated(old = emptySet(), new = setOf("new"))

        h.settings.toggleCustomFilter("new", enabled = null)

        updater.captured(emptySet()) shouldBe setOf("new")
    }

    @Test
    fun `clearCustomFilter removes the id from the set`() = runTest2 {
        val h = harness(initial = setOf("to-clear", "keep"))
        val updater = slot<(Set<String>) -> Set<String>?>()
        io.mockk.coEvery { h.enabledValue.update(capture(updater)) } returns
            eu.darken.sdmse.common.datastore.DataStoreValue.Updated(old = setOf("to-clear", "keep"), new = setOf("keep"))

        h.settings.clearCustomFilter("to-clear")

        updater.captured(setOf("to-clear", "keep")) shouldBe setOf("keep")
    }

    @Test
    fun `isCustomFilterEnabled reads from value`() = runTest2 {
        val h = harness(initial = setOf("enabled-id"))

        h.settings.isCustomFilterEnabled("enabled-id") shouldBe true
        h.settings.isCustomFilterEnabled("not-in-set") shouldBe false
    }
}
