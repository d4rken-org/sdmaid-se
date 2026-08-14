package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.core.automation.errors.LockedAppCacheException
import eu.darken.sdmse.appcleaner.ui.preview.previewAppJunk
import eu.darken.sdmse.automation.core.errors.DisabledAppException
import eu.darken.sdmse.automation.core.errors.InvalidSystemStateException
import eu.darken.sdmse.automation.core.errors.NoSettingsWindowException
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppCleanerExtensionsTest : BaseTest() {

    @Test
    fun `hasData is false when Data is null`() {
        val data: AppCleaner.Data? = null
        data.hasData shouldBe false
    }

    @Test
    fun `hasData is false when junks is empty`() {
        val data = AppCleaner.Data(junks = emptyList())
        data.hasData shouldBe false
    }

    @Test
    fun `hasData is true when junks is non-empty`() {
        val data = AppCleaner.Data(junks = listOf(previewAppJunk()))
        data.hasData shouldBe true
    }

    private fun unclearableJunk(error: Exception = NoSettingsWindowException("no settings window")) =
        previewAppJunk(expendables = null).copy(acsError = error)

    @Test
    fun `a permanently failed inaccessible-only junk is unclearable`() {
        unclearableJunk(NoSettingsWindowException("no settings window")).isUnclearable shouldBe true
        unclearableJunk(DisabledAppException("disabled")).isUnclearable shouldBe true
        unclearableJunk(LockedAppCacheException("locked")).isUnclearable shouldBe true
    }

    @Test
    fun `a junk without a clearing failure is not unclearable`() {
        previewAppJunk(expendables = null).isUnclearable shouldBe false
    }

    @Test
    fun `a transient clearing failure keeps the junk actionable`() {
        // A plan abort caused by system state (screen off, interference) can succeed on retry.
        unclearableJunk(InvalidSystemStateException("screen was off")).isUnclearable shouldBe false
    }

    @Test
    fun `remaining accessible files keep the junk actionable`() {
        previewAppJunk().copy(acsError = NoSettingsWindowException("no settings window")).isUnclearable shouldBe false
    }

    @Test
    fun `hasData stays true while hasActionableData drops for unclearable-only data`() {
        val data = AppCleaner.Data(junks = listOf(unclearableJunk()))
        data.hasData shouldBe true
        data.hasActionableData shouldBe false
    }

    @Test
    fun `hasActionableData is true when at least one junk is actionable`() {
        val data = AppCleaner.Data(junks = listOf(unclearableJunk(), previewAppJunk()))
        data.hasActionableData shouldBe true
    }

    @Test
    fun `hasActionableData is false when Data is null`() {
        val data: AppCleaner.Data? = null
        data.hasActionableData shouldBe false
    }

    @Test
    fun `actionable aggregates exclude unclearable junks but the totals keep them`() {
        val actionable = previewAppJunk()
        val unclearable = unclearableJunk()
        val data = AppCleaner.Data(junks = listOf(actionable, unclearable))

        data.totalSize shouldBe actionable.size + unclearable.size
        data.totalCount shouldBe actionable.itemCount + unclearable.itemCount
        data.actionableSize shouldBe actionable.size
        data.actionableCount shouldBe actionable.itemCount
    }
}
