package eu.darken.sdmse.deduplicator.core.scanner.checksum

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File
import java.io.IOException
import java.time.Instant

class ChecksumSleuthTest : BaseTest() {

    private val testDir = File(IO_TEST_BASEDIR, "ChecksumSleuthTest")
    private val unreadable = mutableSetOf<String>()

    private val gatewaySwitch: GatewaySwitch = mockk<GatewaySwitch>().apply {
        coEvery { file(any(), any()) } answers {
            val path = firstArg<LocalPath>().path
            if (path in unreadable) throw IOException("Permission denied: $path")
            FileSystem.SYSTEM.openReadOnly(File(path).toOkioPath())
        }
    }

    @BeforeEach
    fun setup() {
        testDir.mkdirs()
    }

    @AfterEach
    fun cleanup() {
        testDir.deleteRecursively()
    }

    private fun create(name: String, content: String): LocalPathLookup {
        val file = File(testDir, name).apply { writeText(content) }
        return LocalPathLookup(
            lookedUp = LocalPath.build(file.absolutePath),
            fileType = FileType.FILE,
            size = file.length(),
            modifiedAt = Instant.EPOCH,
            target = null,
        )
    }

    @Test
    fun `identical files form a group and a same-size different file does not`() = runTest {
        val a = create("a", "AAAA")
        val b = create("b", "AAAA")
        val c = create("c", "BBBB")

        val groups = ChecksumSleuth(gatewaySwitch).investigate(listOf(a, b, c).asFlow())

        groups.size shouldBe 1
        groups.single().duplicates.map { it.lookup } shouldContainExactlyInAnyOrder listOf(a, b)
    }

    @Test
    fun `an unreadable file does not drop identical files of the same size`() = runTest {
        val a = create("a", "AAAA")
        val b = create("b", "AAAA")
        val broken = create("broken", "AAAA")
        unreadable.add(broken.path)

        val groups = ChecksumSleuth(gatewaySwitch).investigate(listOf(a, b, broken).asFlow())

        groups.size shouldBe 1
        groups.single().duplicates.map { it.lookup } shouldContainExactlyInAnyOrder listOf(a, b)
    }
}
