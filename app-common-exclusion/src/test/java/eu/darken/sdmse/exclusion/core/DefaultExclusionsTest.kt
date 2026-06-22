package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import io.kotest.matchers.collections.shouldContain
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DefaultExclusionsTest : BaseTest() {

    private val removedValue = mockk<DataStoreValue<Set<String>>>(relaxed = true)
    private val settings = mockk<ExclusionSettings>().apply {
        every { removedDefaultExclusions } returns removedValue
    }

    private fun create() = DefaultExclusions(settings)

    @Test
    fun `defaultIds contains the built-in defaults`() {
        // IDs ignore tags, so the pristine package default resolves to the same ID regardless of tags.
        val pushtanId = PkgExclusion("com.starfinanz.mobile.android.pushtan".toPkgId()).id
        create().defaultIds shouldContain pushtanId
    }

    @Test
    fun `remove ignores IDs that are not built-in defaults`() = runTest {
        create().remove(setOf("not-a-default-id"))
        // Nothing should be written to the removed-defaults set for an unknown/user ID.
        coVerify(exactly = 0) { removedValue.update(any()) }
    }

    @Test
    fun `remove persists a real default ID`() = runTest {
        val defaults = create()
        defaults.remove(setOf(defaults.defaultIds.first()))
        coVerify(exactly = 1) { removedValue.update(any()) }
    }
}
