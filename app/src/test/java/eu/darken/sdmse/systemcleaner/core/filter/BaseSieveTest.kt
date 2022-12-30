package eu.darken.sdmse.systemcleaner.core.filter

import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.files.core.FileType
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.local.LocalPathLookup
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class BaseSieveTest : BaseTest() {

    private val fileForensics: FileForensics = mockk<FileForensics>().apply {

    }

    private fun create(config: BaseSieve.Config) = BaseSieve(
        config = config,
        fileForensics = fileForensics
    )

    @Test
    fun `matching nothing`() = runTest {
        create(BaseSieve.Config()).match(mockk()) shouldBe true
    }

    private val baseLookup = LocalPathLookup(
        lookedUp = LocalPath.build(""),
        fileType = FileType.FILE,
        size = 16,
        modifiedAt = Instant.EPOCH,
        ownership = null,
        permissions = null,
        target = null,
    )

    @Test
    fun `just filetypes`() = runTest {
        val configForFile = BaseSieve.Config(
            targetType = BaseSieve.TargetType.FILE
        )
        val configForDir = BaseSieve.Config(
            targetType = BaseSieve.TargetType.DIRECTORY
        )
        val aFile = mockk<APathLookup<*>>().apply {
            every { fileType } returns FileType.FILE
        }
        create(configForFile).match(aFile) shouldBe true
        create(configForDir).match(aFile) shouldBe false

        val aDirectory = mockk<APathLookup<*>>().apply {
            every { fileType } returns FileType.DIRECTORY
        }
        create(configForFile).match(aDirectory) shouldBe false
        create(configForDir).match(aDirectory) shouldBe true
    }

    @Test
    fun `just basepaths`() = runTest {
        val config = BaseSieve.Config(
            basePaths = setOf(LocalPath.build("abc"))
        )
        create(config).match(
            baseLookup.copy(lookedUp = LocalPath.build("/abc/123"))
        ) shouldBe true
        create(config).match(
            baseLookup.copy(lookedUp = LocalPath.build("/abc"))
        ) shouldBe true
        create(config).match(
            baseLookup.copy(lookedUp = LocalPath.build("/def"))
        ) shouldBe false
    }

    @Test
    fun `just exclusions`() = runTest {
        val config = BaseSieve.Config(
            exclusions = setOf("bc")
        )
        create(config).match(
            baseLookup.copy(lookedUp = LocalPath.build("/def"))
        ) shouldBe true
        create(config).match(
            baseLookup.copy(lookedUp = LocalPath.build("/abc/123"))
        ) shouldBe false
    }

    @Test
    fun `just regex`() = runTest {
        val config = BaseSieve.Config(
            regexes = setOf(Regex("^/a.c/[0-9]+$"))
        )

        create(config).match(
            baseLookup.copy(lookedUp = LocalPath.build("/ac/123"))
        ) shouldBe false
        create(config).match(
            baseLookup.copy(lookedUp = LocalPath.build("/abc/123"))
        ) shouldBe true
    }
}