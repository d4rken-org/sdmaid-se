package eu.darken.sdmse.deduplicator.core

import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterStrategy
import eu.darken.sdmse.deduplicator.core.arbiter.DuplicatesArbiter
import eu.darken.sdmse.deduplicator.core.arbiter.checks.DuplicateTypeCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.LocationCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.MediaProviderCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.ModificationCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.NestingCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.PreferredPathCheck
import eu.darken.sdmse.deduplicator.core.arbiter.checks.SizeCheck
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DuplicatesArbiterTest : BaseTest() {
    private val settings: DeduplicatorSettings = mockk<DeduplicatorSettings>().apply {
        every { keepPreferPaths } returns mockk {
            every { flow } returns flowOf(DeduplicatorSettings.KeepPreferPaths())
        }
    }
    private val duplicateTypeCheck = DuplicateTypeCheck()
    private val mediaProviderCheck: MediaProviderCheck = mockk()
    private val locationCheck: LocationCheck = mockk()
    private val nestingCheck: NestingCheck = mockk()
    private val modificationCheck: ModificationCheck = mockk()
    private val sizeCheck: SizeCheck = mockk()
    private val preferredPathCheck = PreferredPathCheck()

    private fun create() = DuplicatesArbiter(
        settings = settings,
        duplicateTypeCheck = duplicateTypeCheck,
        mediaProviderCheck = mediaProviderCheck,
        locationCheck = locationCheck,
        nestingCheck = nestingCheck,
        modificationCheck = modificationCheck,
        sizeCheck = sizeCheck,
        preferredPathCheck = preferredPathCheck,
    )

    @Test
    fun `decide groups, require non empty groups`() = runTest {
        val arbiter = create()
        val strategy = arbiter.getStrategy()
        val group1 = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("TestID"),
            duplicates = emptySet(),
        )
        val group2 = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("TestID"),
            duplicates = setOf(mockk()),
        )
        shouldThrow<IllegalArgumentException> {
            arbiter.decideGroups(setOf(group1, group2), strategy)
        }.message shouldContain "All groups must be non-empty!"
    }

    @Test
    fun `decide groups, require at least 1 group`() = runTest {
        val arbiter = create()
        val strategy = arbiter.getStrategy()
        shouldThrow<IllegalArgumentException> {
            arbiter.decideGroups(setOf(), strategy)
        }.message shouldContain "Must pass at least 1 group!"
    }

    @Test
    fun `decide groups`() = runTest {
        val arbiter = create()
        val strategy = arbiter.getStrategy()
        val group1 = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("TestID1"),
            duplicates = setOf(mockk()),
        )
        val group2 = PHashDuplicate.Group(
            identifier = Duplicate.Group.Id("TestID2"),
            duplicates = setOf(mockk()),
        )

        arbiter.decideGroups(setOf(group1, group2), strategy) shouldBe (group1 to setOf(group2))
        arbiter.decideGroups(setOf(group2, group1), strategy) shouldBe (group1 to setOf(group2))
    }

    @Test
    fun `decide duplicates, require at least 1 duplicate`() = runTest {
        val arbiter = create()
        val strategy = arbiter.getStrategy()
        shouldThrow<IllegalArgumentException> {
            arbiter.decideDuplicates(setOf(), strategy)
        }.message shouldContain "Must pass at least 1 duplicate!"
    }
}