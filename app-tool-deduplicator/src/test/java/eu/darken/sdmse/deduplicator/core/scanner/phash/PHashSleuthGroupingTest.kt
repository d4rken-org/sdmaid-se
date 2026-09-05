package eu.darken.sdmse.deduplicator.core.scanner.phash

import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.deduplicator.core.scanner.phash.phash.PHashBits
import eu.darken.sdmse.deduplicator.core.scanner.phash.phash.PHasher
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PHashSleuthGroupingTest : BaseTest() {

    private val sleuth = PHashSleuth(mockk(), mockk(), mockk())

    private fun lookup(path: String): APathLookup<*> = mockk {
        every { this@mockk.path } returns path
    }

    private fun result(bits: Long) = PHasher.Result(hash = PHashBits(bits))

    private fun Set<Set<Pair<APathLookup<*>, Double>>>.paths() =
        map { group -> group.map { it.first.path }.toSet() }.toSet()

    // A~B: 3 differing bits (61/64 > 0.95), B~C: 3 differing bits, A~C: 6 differing bits (58/64 < 0.95)
    private val a = lookup("/dcim/a.jpg") to result(0L)
    private val b = lookup("/dcim/b.jpg") to result(0b111L)
    private val c = lookup("/dcim/c.jpg") to result(0b111111L)

    @Test
    fun `chain grouping does not depend on input order`() = runTest {
        val forward = sleuth.groupBySimilarity(linkedMapOf(a, b, c)).paths()
        val reversed = sleuth.groupBySimilarity(linkedMapOf(c, b, a)).paths()

        forward shouldBe setOf(setOf("/dcim/a.jpg", "/dcim/b.jpg"))
        reversed shouldBe forward
    }

    @Test
    fun `target without similar sibling produces no group`() = runTest {
        val lone = lookup("/dcim/z.jpg") to result(-1L)
        val groups = sleuth.groupBySimilarity(linkedMapOf(a, b, lone)).paths()

        groups shouldBe setOf(setOf("/dcim/a.jpg", "/dcim/b.jpg"))
    }

    @Test
    fun `target similarity is the average over its group`() = runTest {
        // target 0L; siblings differ by 1 bit (63/64) and 3 bits (61/64); average is 62/64
        val target = lookup("/dcim/t.jpg") to result(0L)
        val near = lookup("/dcim/u.jpg") to result(1L)
        val far = lookup("/dcim/v.jpg") to result(0b111L)

        listOf(linkedMapOf(target, near, far), linkedMapOf(far, near, target)).forEach { input ->
            val group = sleuth.groupBySimilarity(input).single()
            val byPath = group.associate { it.first.path to it.second }

            byPath shouldBe mapOf(
                "/dcim/t.jpg" to 62.0 / 64.0,
                "/dcim/u.jpg" to 63.0 / 64.0,
                "/dcim/v.jpg" to 61.0 / 64.0,
            )
        }
    }
}
