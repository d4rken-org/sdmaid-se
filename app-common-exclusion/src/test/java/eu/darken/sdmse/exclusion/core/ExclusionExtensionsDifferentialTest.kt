package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.RawPath
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.SegmentExclusion
import eu.darken.sdmse.exclusion.core.types.excludeNested
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

/**
 * `Collection<Exclusion.Path>.excludeNested` replaced a fold that ran the whole two-pass removal once
 * per exclusion with a single pass over the paths. Its KDoc claims the two produce the same fixed
 * point, and that the single pass therefore no longer depends on the order of the exclusions.
 *
 * This test makes that claim differential: the old fold is kept here as an oracle and both are run
 * over every exclusion subset of size one to three drawn from a small tree, in every order.
 */
class ExclusionExtensionsDifferentialTest : BaseTest() {

    @Test
    fun `local - excludeNested agrees with the pre-index fold in every order`() = runTest {
        val pool: List<Exclusion.Path> = LOCAL_TREE.map { PathExclusion(it) } +
                SegmentExclusion(segs("b"), allowPartial = false, ignoreCase = true)
        assertAgreement(pool, LOCAL_TREE)
    }

    @Test
    fun `raw - excludeNested agrees with the pre-index fold in every order`() = runTest {
        // No SegmentExclusion here: RawPath.segments throws NotImplementedError, and the fold walks
        // into it while the index answers RAW paths from its own keys first.
        assertAgreement(RAW_TREE.map { PathExclusion(it) }, RAW_TREE)
    }

    private suspend fun <T : APath> assertAgreement(pool: List<Exclusion.Path>, paths: Set<T>) {
        (1..3).forEach { size ->
            combinations(pool, size).forEach { combination ->
                val expected = combination.referenceExcludeNested(paths)

                permutations(combination).forEach { ordered ->
                    withClue({ "exclusions=${ordered.map { it.id }}" }) {
                        // The oracle has to be order independent as well, otherwise "agrees with the
                        // fold" would only be a claim about one arbitrary order.
                        ordered.referenceExcludeNested(paths) shouldBe expected
                        ordered.excludeNested(paths) shouldBe expected
                    }
                }
            }
        }
    }

    /** The pre-index implementation, kept here as the differential oracle. */
    private suspend fun <T : APath> Collection<Exclusion.Path>.referenceExcludeNested(paths: Collection<T>): Set<T> {
        var temp = paths.toSet()
        forEach { exclusion ->
            if (temp.isEmpty()) return@forEach
            val excluded = mutableSetOf<T>()
            val afterFirst = temp.filter { path ->
                val hit = exclusion.match(path)
                if (hit) excluded.add(path)
                !hit
            }
            temp = afterFirst.filterNot { path -> excluded.any { path.isAncestorOf(it) } }.toSet()
        }
        return temp
    }

    private fun <T> combinations(items: List<T>, size: Int): List<List<T>> = when {
        size == 0 -> listOf(emptyList())
        items.size < size -> emptyList()
        else -> combinations(items.drop(1), size - 1).map { listOf(items.first()) + it } +
                combinations(items.drop(1), size)
    }

    private fun <T> permutations(items: List<T>): List<List<T>> {
        if (items.size <= 1) return listOf(items)
        return items.indices.flatMap { index ->
            val rest = items.toMutableList().apply { removeAt(index) }
            permutations(rest).map { listOf(items[index]) + it }
        }
    }

    companion object {
        // Every prefix of /a/a, /a/b, /a/b/a, /a/b/b, /b and /b/a.
        private val LOCAL_TREE: Set<LocalPath> = setOf(
            "/a",
            "/a/a",
            "/a/b",
            "/a/b/a",
            "/a/b/b",
            "/b",
            "/b/a",
        ).map { LocalPath(File(it)) }.toSet()

        private val RAW_TREE: Set<RawPath> = setOf(
            RawPath("/a"),
            RawPath("/a/b"),
            RawPath("/a/b/a"),
            RawPath("/b"),
        )
    }
}
