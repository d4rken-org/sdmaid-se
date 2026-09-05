package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_MEDIA
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.isChildOf
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.SystemCrawlerSieve
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EmptyDirectoryFilterTest : SystemCleanerFilterTest() {
    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = EmptyDirectoryFilter(
        sieveFactory = object : SystemCrawlerSieve.Factory {
            override fun create(config: SystemCrawlerSieve.Config): SystemCrawlerSieve =
                SystemCrawlerSieve(config, fileForensics)
        },
        gatewaySwitch = gatewaySwitch,
    )

    private val counts = mutableMapOf<String, Int>()

    /**
     * Re-stubs an already mocked directory so every listing is counted. The `yield()` gives a
     * second coroutine the chance to interleave while this listing is in flight.
     */
    private suspend fun countListings(dir: APathLookup<*>) {
        val children = gatewaySwitch.lookupFiles(dir.lookedUp).toList()
        coEvery { gatewaySwitch.lookupFiles(dir.lookedUp) } coAnswers {
            counts.merge(dir.path, 1, Int::plus)
            flow {
                yield()
                children.forEach { emit(it) }
            }
        }
    }

    private fun Collection<APathLookup<*>>.pick(areaPath: String, suffix: String): APathLookup<*> =
        single { it.path.startsWith("$areaPath/") && it.path.endsWith("/$suffix") }

    @Test fun `test basic protected dirs`() = runTest {
        neg(SDCARD, "afile", Flag.File)
        pos(SDCARD, "SomethingelseSDCARD", Flag.Dir)
        neg(PUBLIC_MEDIA, "afile", Flag.File)
        neg(PUBLIC_MEDIA, "emptytopleveldir", Flag.Dir)
        neg(PUBLIC_MEDIA, "topleveldir", Flag.Dir)
        pos(PUBLIC_MEDIA, "topleveldir/emptybottomleveldir", Flag.Dir)
        neg(PUBLIC_DATA, "afile", Flag.File)
        neg(PUBLIC_DATA, "anotemptydir", Flag.Dir)
        neg(PUBLIC_DATA, "com.some.package", Flag.Dir)
        neg(PUBLIC_DATA, "com.some.package/files", Flag.Dir)
        neg(PUBLIC_DATA, "com.some.package/cache", Flag.Dir)
        neg(SDCARD, "DCIM", Flag.Dir)
        pos(SDCARD, "DCIM/EmptyDir", Flag.Dir)
        neg(SDCARD, "Camera", Flag.Dir)
        pos(SDCARD, "Camera/EmptyDir", Flag.Dir)
        neg(SDCARD, "Photos", Flag.Dir)
        pos(SDCARD, "Photos/EmptyDir", Flag.Dir)
        neg(SDCARD, "Music", Flag.Dir)
        pos(SDCARD, "Music/EmptyDir", Flag.Dir)
        neg(SDCARD, "Pictures", Flag.Dir)
        pos(SDCARD, "Pictures/EmptyDir", Flag.Dir)

//        // https://github.com/d4rken/sdmaid-public/issues/1435
        neg(SDCARD, ".stfolder", Flag.Dir)

        confirm(create())
    }

    @Test fun `empty directories - basic`() = runTest {
        neg(SDCARD, "1", Flag.Dir)
        neg(SDCARD, "1/1", Flag.File)
        pos(SDCARD, "2", Flag.Dir)
        pos(SDCARD, "2/2", Flag.Dir)

        confirm(create())
    }

    @Test fun `empty directories - nested`() = runTest {
        neg(SDCARD, "1", Flag.File)
        neg(SDCARD, "2", Flag.Dir)
        neg(SDCARD, "2/2", Flag.File)

        pos(SDCARD, "3", Flag.Dir)
        pos(SDCARD, "3/3", Flag.Dir)
        pos(SDCARD, "3/3/3", Flag.Dir)

        pos(SDCARD, "4", Flag.Dir)
        pos(SDCARD, "4/5", Flag.Dir)
        pos(SDCARD, "4/5/5", Flag.Dir)
        pos(SDCARD, "4/6", Flag.Dir)
        pos(SDCARD, "4/6/6", Flag.Dir)
        confirm(create())
    }

    @Test fun `empty directories - nested but blocked`() = runTest {
        neg(SDCARD, "4", Flag.Dir)
        neg(SDCARD, "4/file", Flag.File)

        pos(SDCARD, "4/5", Flag.Dir)
        pos(SDCARD, "4/5/5", Flag.Dir)

        neg(SDCARD, "4/6", Flag.Dir)
        neg(SDCARD, "4/6/6", Flag.Dir)
        neg(SDCARD, "4/6/6/file", Flag.File)

        confirm(create())
    }

    @Test fun `empty directories - with large node sizes`() = runTest {
        neg(SDCARD, "0", Flag.File)
        pos(SDCARD, "1", Flag.Dir, Flag.Size(262144))
        pos(SDCARD, "2", Flag.Dir, Flag.Size(524288))
        pos(SDCARD, "3", Flag.Dir, Flag.Size(1048576))

        confirm(create())
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun `huge directories - listing is aborted at the first blocking child`() = runTest {
        val dirs = doMock(SDCARD, "huge", null, Flag.Dir)
        negatives.addAll(dirs)
        val blockers = doMock(SDCARD, "huge/blocker", null, Flag.File)
        negatives.addAll(blockers)

        dirs.forEach { dir ->
            val blocker = blockers.single { it.lookedUp.isChildOf(dir.lookedUp) }
            // Materializing the listing (instead of aborting after the first child) trips the error.
            coEvery { gatewaySwitch.lookupFiles(dir.lookedUp) } returns flow {
                emit(blocker as APathLookup<APath>)
                throw AssertionError("Should not enumerate past the first blocking child: ${dir.path}")
            }
        }

        pos(SDCARD, "empty", Flag.Dir)

        confirm(create())
    }

    @Test fun `nested directories are listed once per scan, not once per ancestor`() = runTest {
        pos(SDCARD, "tree", Flag.Dir)
        pos(SDCARD, "tree/a", Flag.Dir)
        pos(SDCARD, "tree/a/b", Flag.Dir)
        pos(SDCARD, "tree/a/b/c", Flag.Dir)

        neg(SDCARD, "full", Flag.Dir)
        neg(SDCARD, "full/a", Flag.Dir)
        neg(SDCARD, "full/a/b", Flag.Dir)
        neg(SDCARD, "full/a/b/file", Flag.File)

        val dirs = (positives + negatives).filter { it.fileType == FileType.DIRECTORY }
        dirs.forEach { countListings(it) }

        val filter = create()
        filter.initialize()

        val crawlOrder = listOf("tree", "tree/a", "tree/a/b", "tree/a/b/c", "full", "full/a", "full/a/b")
        listOf(storageSdcard1, storageSdcard2).forEach { area ->
            crawlOrder.forEach { suffix ->
                val candidate = dirs.pick(area.path.path, suffix)
                val result = filter.match(candidate)
                withClue(candidate.path) {
                    if (suffix.startsWith("tree")) result shouldNotBe null else result shouldBe null
                }
            }
        }

        dirs.forEach { withClue(it.path) { counts[it.path] shouldBe 1 } }
    }

    @Test fun `an unreadable directory is listed once and blocks every ancestor`() = runTest {
        neg(SDCARD, "broken", Flag.Dir)
        neg(SDCARD, "broken/a", Flag.Dir)
        neg(SDCARD, "broken/a/b", Flag.Dir)

        val dirs = negatives.filter { it.fileType == FileType.DIRECTORY }
        val (unreadable, readable) = dirs.partition { it.path.endsWith("/broken/a/b") }
        readable.forEach { countListings(it) }
        unreadable.forEach { b ->
            coEvery { gatewaySwitch.lookupFiles(b.lookedUp) } coAnswers {
                counts.merge(b.path, 1, Int::plus)
                flow { throw ReadException(path = b.lookedUp) }
            }
        }

        val filter = create()
        filter.initialize()

        listOf(storageSdcard1, storageSdcard2).forEach { area ->
            listOf("broken", "broken/a", "broken/a/b").forEach { suffix ->
                val candidate = dirs.pick(area.path.path, suffix)
                withClue(candidate.path) {
                    shouldThrow<ReadException> { filter.match(candidate) }
                }
            }
        }

        dirs.forEach { withClue(it.path) { counts[it.path] shouldBe 1 } }
    }

    @Test fun `concurrent evaluations of an ancestor and its child share one listing`() = runTest {
        pos(SDCARD, "tree", Flag.Dir)
        pos(SDCARD, "tree/a", Flag.Dir)
        pos(SDCARD, "tree/a/b", Flag.Dir)

        val dirs = positives.filter { it.fileType == FileType.DIRECTORY }
        dirs.forEach { countListings(it) }

        val filter = create()
        filter.initialize()

        val areaPath = storageSdcard1.path.path
        val tree = dirs.pick(areaPath, "tree")
        val treeA = dirs.pick(areaPath, "tree/a")
        val treeAB = dirs.pick(areaPath, "tree/a/b")

        val outer = async { filter.match(tree) }
        val inner = async { filter.match(treeA) }

        outer.await() shouldNotBe null
        inner.await() shouldNotBe null

        listOf(tree, treeA, treeAB).forEach { withClue(it.path) { counts[it.path] shouldBe 1 } }
    }
}