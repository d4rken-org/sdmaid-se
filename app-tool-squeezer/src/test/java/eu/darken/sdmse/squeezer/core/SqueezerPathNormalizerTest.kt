package eu.darken.sdmse.squeezer.core

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.storage.PathMapper
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SqueezerPathNormalizerTest : BaseTest() {

    private val pathMapper: PathMapper = mockk()

    @Test
    fun `empty input returns empty result`() = runTest {
        val result = SqueezerPathNormalizer.normalize(emptyList(), pathMapper)

        result.accepted shouldBe emptySet()
        result.dropped shouldBe emptyList()
    }

    @Test
    fun `LocalPath passes through untouched`() = runTest {
        val local1 = LocalPath.build("/storage/emulated/0/DCIM")
        val local2 = LocalPath.build("/storage/emulated/0/Pictures")

        val result = SqueezerPathNormalizer.normalize(setOf(local1, local2), pathMapper)

        result.accepted shouldContainExactlyInAnyOrder setOf(local1, local2)
        result.dropped shouldBe emptyList()
    }

    @Test
    fun `mappable SAFPath gets mapped to LocalPath`() = runTest {
        val safPath: SAFPath = mockk()
        val mappedLocal = LocalPath.build("/storage/emulated/0/DCIM")
        coEvery { pathMapper.toLocalPath(safPath) } returns mappedLocal

        val result = SqueezerPathNormalizer.normalize(setOf(safPath), pathMapper)

        result.accepted shouldContainExactlyInAnyOrder setOf(mappedLocal)
        result.dropped shouldBe emptyList()
    }

    @Test
    fun `unmappable SAFPath is dropped`() = runTest {
        val safPath: SAFPath = mockk()
        coEvery { pathMapper.toLocalPath(safPath) } returns null

        val result = SqueezerPathNormalizer.normalize(setOf(safPath), pathMapper)

        result.accepted shouldBe emptySet()
        result.dropped shouldContainExactly listOf(safPath)
    }

    @Test
    fun `mixed input separates cleanly`() = runTest {
        val local = LocalPath.build("/storage/emulated/0/DCIM")
        val mappableSaf: SAFPath = mockk()
        val mappedLocal = LocalPath.build("/storage/emulated/0/Pictures")
        val unmappableSaf: SAFPath = mockk()

        coEvery { pathMapper.toLocalPath(mappableSaf) } returns mappedLocal
        coEvery { pathMapper.toLocalPath(unmappableSaf) } returns null

        val result = SqueezerPathNormalizer.normalize(
            input = setOf(local, mappableSaf, unmappableSaf),
            pathMapper = pathMapper,
        )

        result.accepted shouldContainExactlyInAnyOrder setOf(local, mappedLocal)
        result.dropped shouldContainExactly listOf(unmappableSaf)
    }

    @Test
    fun `multiple unmappable SAFPaths all dropped`() = runTest {
        val safA: SAFPath = mockk()
        val safB: SAFPath = mockk()
        val safC: SAFPath = mockk()
        coEvery { pathMapper.toLocalPath(any()) } returns null

        val result = SqueezerPathNormalizer.normalize(setOf(safA, safB, safC), pathMapper)

        result.accepted shouldBe emptySet()
        result.dropped.size shouldBe 3
    }
}
