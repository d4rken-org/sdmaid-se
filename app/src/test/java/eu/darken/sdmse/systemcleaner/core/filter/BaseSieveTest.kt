package eu.darken.sdmse.systemcleaner.core.filter

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class BaseSieveTest : BaseTest() {

    private val basePath = LocalPath.build("sdcard")
    private val fileForensics: FileForensics = mockk<FileForensics>().apply {
        coEvery { identifyArea(any()) } answers {
            AreaInfo(
                dataArea = mockk<DataArea>().apply {
                    every { type } returns DataArea.Type.SDCARD
                },
                file = arg(0),
                prefix = basePath,
                isBlackListLocation = false,
            )
        }
    }

    private fun create(config: BaseSieve.Config) = BaseSieve(
        config = config,
        fileForensics = fileForensics
    )

    @Test
    fun `matching nothing`() = runTest {
        create(BaseSieve.Config()).match(baseLookup).matches shouldBe true
    }

    private val baseLookup = LocalPathLookup(
        lookedUp = basePath,
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
        val aFile = baseLookup.copy(
            fileType = FileType.FILE
        )
        create(configForFile).match(aFile).matches shouldBe true
        create(configForDir).match(aFile).matches shouldBe false

        val aDirectory = baseLookup.copy(
            fileType = FileType.DIRECTORY
        )
        create(configForFile).match(aDirectory).matches shouldBe false
        create(configForDir).match(aDirectory).matches shouldBe true
    }

    @Test
    fun `just path ancestors`() = runTest {
        val config = BaseSieve.Config(
            pathAncestors = setOf(segs("abc"))
        )
        create(config).match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe true
        create(config).match(
            baseLookup.copy(lookedUp = basePath.child("/abc"))
        ).matches shouldBe false
        create(config).match(
            baseLookup.copy(lookedUp = basePath.child("/def"))
        ).matches shouldBe false
    }

    @Test
    fun `just path prefixes`() = runTest {
        val config = BaseSieve.Config(
            pathPrefixes = setOf(segs("abc", "12"))
        )
        create(config).match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe true
        create(config).match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123/456"))
        ).matches shouldBe true
        create(config).match(
            baseLookup.copy(lookedUp = basePath.child("/abc"))
        ).matches shouldBe false
        create(config).match(
            baseLookup.copy(lookedUp = basePath.child("/def"))
        ).matches shouldBe false
    }

    @Test
    fun `just exclusions`() = runTest {
        create(
            BaseSieve.Config(exclusions = setOf(BaseSieve.Exclusion(segs("bc"), allowPartial = true)))
        ).match(
            baseLookup.copy(lookedUp = basePath.child("/def"))
        ).matches shouldBe true
        create(
            BaseSieve.Config(exclusions = setOf(BaseSieve.Exclusion(segs("abc"), allowPartial = false)))
        ).match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe false

        create(
            BaseSieve.Config(exclusions = setOf(BaseSieve.Exclusion(segs("bc"), allowPartial = false)))
        ).match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe true
        create(
            BaseSieve.Config(exclusions = setOf(BaseSieve.Exclusion(segs("bc"), allowPartial = true)))
        ).match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe false
    }

    @Test
    fun `just regex`() = runTest {
        val config = BaseSieve.Config(
            regexes = setOf(Regex(".+/a.c/[0-9]+$"))
        )

        create(config).match(
            baseLookup.copy(lookedUp = basePath.child("/ac/123"))
        ).matches shouldBe false
        create(config).match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe true
    }
}