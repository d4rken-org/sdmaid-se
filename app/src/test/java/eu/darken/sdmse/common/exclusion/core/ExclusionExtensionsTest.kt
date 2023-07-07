package eu.darken.sdmse.common.exclusion.core

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.serialization.SerializationAppModule
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.SegmentExclusion
import eu.darken.sdmse.exclusion.core.types.excludeNested
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class ExclusionExtensionsTest : BaseTest() {
    private val testFile = File(IO_TEST_BASEDIR, "testfile")
    private val moshi = SerializationAppModule().moshi()

    @AfterEach
    fun cleanup() {
        testFile.delete()
    }

    @Test
    fun `exclude nested - segment - local path`() = runTest {
        val excl = SegmentExclusion(segs("path", "item"), allowPartial = false, ignoreCase = true)
        val paths = setOf(
            LocalPath.build("altroot"),
            LocalPath.build("root"),
            LocalPath.build("root", "test"),
            LocalPath.build("root", "test", "path"),
            LocalPath.build("root", "test", "path", "item", "subitem"),
            LocalPath.build("root", "alt"),
            LocalPath.build("root", "alt", "path"),
            LocalPath.build("root", "alt", "path", "item"),
            LocalPath.build("root", "alt", "path", "item", "subitem"),
        )

        excl.excludeNested(paths) shouldBe setOf(
            LocalPath.build("altroot"),
        )
    }

    @Test
    fun `exclude nested - path - local path`() = runTest {
        val excl = PathExclusion(LocalPath.build("root", "test", "path"))
        val paths = setOf(
            LocalPath.build("root"),
            LocalPath.build("root", "test"),
            LocalPath.build("root", "test", "path"),
            LocalPath.build("root", "test", "path", "item"),
            LocalPath.build("root", "test", "path", "item", "subitem"),
            LocalPath.build("root", "alt"),
            LocalPath.build("root", "alt", "path"),
            LocalPath.build("root", "alt", "path", "item"),
            LocalPath.build("root", "alt", "path", "item", "subitem"),
        )

        excl.excludeNested(paths) shouldBe setOf(
            LocalPath.build("root", "alt"),
            LocalPath.build("root", "alt", "path"),
            LocalPath.build("root", "alt", "path", "item"),
            LocalPath.build("root", "alt", "path", "item", "subitem"),
        )
    }

    @Test
    fun `exclude multiple nested - segment - local path`() = runTest {
        val excls = setOf(
            SegmentExclusion(segs("subitem1"), allowPartial = false, ignoreCase = true),
            SegmentExclusion(segs("subitem2"), allowPartial = false, ignoreCase = true),
        )
        val paths = setOf(
            LocalPath.build("altroot"),
            LocalPath.build("root", "test"),
            LocalPath.build("root", "test", "path"),
            LocalPath.build("root", "test", "path", "item"),
            LocalPath.build("root", "test", "path", "item", "subitem1"),
            LocalPath.build("root", "test", "path", "item", "subitem2"),
            LocalPath.build("root", "test", "path", "item", "subitem3"),
            LocalPath.build("root", "alt"),
            LocalPath.build("root", "alt", "path"),
            LocalPath.build("root", "alt", "path", "item"),
            LocalPath.build("root", "alt", "path", "item", "subitem1"),
            LocalPath.build("root", "alt", "path", "item", "subitem2"),
            LocalPath.build("root", "alt", "path", "item", "subitem3"),
        )

        excls.excludeNested(paths) shouldBe setOf(
            LocalPath.build("altroot"),
            LocalPath.build("root", "test", "path", "item", "subitem3"),
            LocalPath.build("root", "alt", "path", "item", "subitem3"),
        )
    }

    @Test
    fun `exclude multiple nested - path - local path`() = runTest {
        val excls = setOf(
            PathExclusion(LocalPath.build("root", "test", "path", "item", "subitem1")),
            PathExclusion(LocalPath.build("root", "alt", "path", "item", "subitem1")),
        )
        val paths = setOf(
            LocalPath.build("altroot"),
            LocalPath.build("root"),
            LocalPath.build("root", "test"),
            LocalPath.build("root", "test", "path"),
            LocalPath.build("root", "test", "path", "item"),
            LocalPath.build("root", "test", "path", "item", "subitem1"),
            LocalPath.build("root", "test", "path", "item", "subitem2"),
            LocalPath.build("root", "alt"),
            LocalPath.build("root", "alt", "path"),
            LocalPath.build("root", "alt", "path", "item"),
            LocalPath.build("root", "alt", "path", "item", "subitem1"),
            LocalPath.build("root", "alt", "path", "item", "subitem2"),
        )

        excls.excludeNested(paths) shouldBe setOf(
            LocalPath.build("altroot"),
            LocalPath.build("root", "test", "path", "item", "subitem2"),
            LocalPath.build("root", "alt", "path", "item", "subitem2"),
        )
    }
}