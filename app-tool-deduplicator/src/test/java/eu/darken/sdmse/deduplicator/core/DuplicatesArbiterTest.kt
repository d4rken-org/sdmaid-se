package eu.darken.sdmse.deduplicator.core

import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DuplicatesArbiterTest : BaseTest() {
    private val settings: DeduplicatorSettings = mockk<DeduplicatorSettings>().apply {
        every { arbiterConfig } returns mockk {
            every { flow } returns flowOf(DeduplicatorSettings.ArbiterConfig())
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

    @Test
    fun `criteria at top of list have highest priority`() = runTest {
        // Create two mock duplicates - one for "first" and one for "second" outcomes
        val dupe1 = mockk<Duplicate>()
        val dupe2 = mockk<Duplicate>()

        // Mock locationCheck to prefer dupe1 (sorts it first)
        coEvery { locationCheck.favorite(any(), any()) } answers {
            val dupes = firstArg<List<Duplicate>>()
            dupes.sortedBy { if (it == dupe1) 0 else 1 }
        }

        // Mock nestingCheck to prefer dupe2 (sorts it first)
        coEvery { nestingCheck.favorite(any(), any()) } answers {
            val dupes = firstArg<List<Duplicate>>()
            dupes.sortedBy { if (it == dupe2) 0 else 1 }
        }

        val arbiter = create()

        // Strategy with Location at TOP (should have highest priority)
        val strategyLocationFirst = ArbiterStrategy(
            criteria = listOf(
                ArbiterCriterium.Location(), // TOP = highest priority
                ArbiterCriterium.Nesting(),  // BOTTOM = lower priority
            ),
        )

        // Location is at TOP, should have highest priority, so dupe1 is kept
        val (keeper1, _) = arbiter.decideDuplicates(listOf(dupe1, dupe2), strategyLocationFirst)
        keeper1 shouldBe dupe1

        // Strategy with Nesting at TOP (should have highest priority)
        val strategyNestingFirst = ArbiterStrategy(
            criteria = listOf(
                ArbiterCriterium.Nesting(),  // TOP = highest priority
                ArbiterCriterium.Location(), // BOTTOM = lower priority
            ),
        )

        // Nesting is at TOP, should have highest priority, so dupe2 is kept
        val (keeper2, _) = arbiter.decideDuplicates(listOf(dupe1, dupe2), strategyNestingFirst)
        keeper2 shouldBe dupe2
    }
}