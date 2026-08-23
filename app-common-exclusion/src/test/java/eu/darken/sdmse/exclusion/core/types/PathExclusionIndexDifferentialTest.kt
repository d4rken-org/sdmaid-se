package eu.darken.sdmse.exclusion.core.types

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.RawPath
import eu.darken.sdmse.common.files.local.LocalPath
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

/**
 * The hand-written cases in [PathExclusionIndexTest] pin the rules the index is built on. This test
 * pins the equivalence itself: over a generated corpus the index must answer exactly what the
 * linear `any { it.match(candidate) }` it replaced answers, for every ordered
 * (exclusion, candidate) pair.
 *
 * The corpus is every string over `/`, `a` and `b` up to length 5, which covers separator
 * boundaries, repeated and trailing separators, the root, the empty path, relative paths and
 * strings that share a prefix without sharing a path component.
 */
class PathExclusionIndexDifferentialTest : BaseTest() {

    @Test
    fun `local - the index agrees with the linear scan for every pair`() = runTest {
        // java.io.File normalizes, so "/a/" and "//a" collapse onto "/a" and would be tested twice.
        val corpus = CORPUS.map { LocalPath(File(it)) }.distinctBy { it.path }
        assertAgreement(corpus)
    }

    @Test
    fun `raw - the index agrees with the linear scan for every pair`() = runTest {
        // RAW paths are not normalized, the string is the path.
        assertAgreement(CORPUS.map { RawPath(it) })
    }

    private suspend fun assertAgreement(corpus: List<APath>) {
        corpus.forEach { exclusionPath ->
            val exclusions = listOf<Exclusion.Path>(PathExclusion(exclusionPath))
            val index = PathExclusionIndex(exclusions)

            corpus.forEach { candidate ->
                val expected = exclusions.any { it.match(candidate) }
                // Lazy clue: the corpus produces six figures of pairs, only the failing one needs a message.
                withClue({ "exclusion='${exclusionPath.path}' candidate='${candidate.path}'" }) {
                    index.matches(candidate) shouldBe expected
                }
            }
        }
    }

    companion object {
        private val CORPUS: List<String> = run {
            val alphabet = listOf("/", "a", "b")
            val all = mutableListOf("")
            var current = listOf("")
            repeat(5) {
                current = current.flatMap { prefix -> alphabet.map { prefix + it } }
                all.addAll(current)
            }
            all
        }

        init {
            check("" in CORPUS)
        }
    }
}
