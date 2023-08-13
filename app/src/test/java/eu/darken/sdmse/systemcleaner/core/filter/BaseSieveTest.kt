package eu.darken.sdmse.systemcleaner.core.filter

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.systemcleaner.core.BaseSieve
import eu.darken.sdmse.systemcleaner.core.BaseSieve.Config
import eu.darken.sdmse.systemcleaner.core.BaseSieve.Criterium.Mode
import eu.darken.sdmse.systemcleaner.core.BaseSieve.SegmentCriterium
import eu.darken.sdmse.systemcleaner.core.BaseSieve.TargetType
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

    private suspend fun Config.match(subject: APathLookup<*>) = BaseSieve(
        config = this,
        fileForensics = fileForensics
    ).match(subject)

    @Test
    fun `matching nothing`() = runTest {
        Config().match(baseLookup).matches shouldBe true
    }

    private val baseLookup = LocalPathLookup(
        lookedUp = basePath,
        fileType = FileType.FILE,
        size = 16,
        modifiedAt = Instant.EPOCH,
        target = null,
    )

    @Test
    fun `just filetypes`() = runTest {
        val configForFile = Config(
            targetTypes = setOf(TargetType.FILE)
        )
        val configForDir = Config(
            targetTypes = setOf(TargetType.DIRECTORY)
        )
        val aFile = baseLookup.copy(
            fileType = FileType.FILE
        )
        configForFile.match(aFile).matches shouldBe true
        configForDir.match(aFile).matches shouldBe false

        val aDirectory = baseLookup.copy(
            fileType = FileType.DIRECTORY
        )
        configForFile.match(aDirectory).matches shouldBe false
        configForDir.match(aDirectory).matches shouldBe true
    }

    @Test
    fun `just path contains`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("bc/12"), mode = Mode.CONTAIN, allowPartial = true)
            )
        ).match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe true

        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("123"), mode = Mode.CONTAIN, allowPartial = true)
            )
        ).match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe true

        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("/123"), mode = Mode.CONTAIN, allowPartial = true)
            )
        ).match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe true

        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("/sdcard"), mode = Mode.CONTAIN, allowPartial = true)
            )
        ).match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe true

        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("123/"), mode = Mode.CONTAIN, allowPartial = true)
            )
        ).match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe false

        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("abc"), mode = Mode.CONTAIN, allowPartial = true)
            )
        ).match(
            baseLookup.copy(lookedUp = basePath.child("/ABC/123"))
        ).matches shouldBe true

        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("abc"),
                    mode = Mode.CONTAIN,
                    allowPartial = true,
                    ignoreCase = false
                )
            )
        ).match(
            baseLookup.copy(lookedUp = basePath.child("/ABC/123"))
        ).matches shouldBe false
    }

    @Test
    fun `just pfp ancestors`() = runTest {
        val config = Config(
            pfpCriteria = setOf(
                SegmentCriterium(segs("abc"), mode = Mode.START, allowPartial = false)
            )
        )
        config.match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe true
        config.match(
            baseLookup.copy(lookedUp = basePath.child("/abc"))
        ).matches shouldBe false
        config.match(
            baseLookup.copy(lookedUp = basePath.child("abc"))
        ).matches shouldBe false

        config.match(
            baseLookup.copy(lookedUp = basePath.child("/def"))
        ).matches shouldBe false

        config.match(
            baseLookup.copy(lookedUp = basePath.child("/abc"))
        ).matches shouldBe false
    }


    @Test
    fun `just pfp prefixes`() = runTest {
        val config = Config(
            pfpCriteria = setOf(
                SegmentCriterium(segs("abc", "12"), mode = Mode.START, allowPartial = true)
            )
        )
        config.match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe true
        config.match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123/456"))
        ).matches shouldBe true
        config.match(
            baseLookup.copy(lookedUp = basePath.child("/abc"))
        ).matches shouldBe false
        config.match(
            baseLookup.copy(lookedUp = basePath.child("/def"))
        ).matches shouldBe false
    }

    @Test
    fun `just exclusions`() = runTest {
        Config(
            exclusions = setOf(
                SegmentCriterium(segs("bc"), mode = Mode.CONTAIN, allowPartial = true)
            )
        ).match(
            baseLookup.copy(lookedUp = basePath.child("/def"))
        ).matches shouldBe true
        Config(
            exclusions = setOf(
                SegmentCriterium(segs("abc"), mode = Mode.CONTAIN, allowPartial = false)
            )
        ).match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe false

        Config(
            exclusions = setOf(
                SegmentCriterium(segs("bc"), mode = Mode.CONTAIN, allowPartial = false)
            )
        ).match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe true
        Config(
            exclusions = setOf(
                SegmentCriterium(segs("bc"), mode = Mode.CONTAIN, allowPartial = true)
            )
        ).match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe false
    }

    @Test
    fun `just regex`() = runTest {
        val config = Config(
            regexes = setOf(Regex(".+/a.c/[0-9]+$"))
        )

        config.match(
            baseLookup.copy(lookedUp = basePath.child("/ac/123"))
        ).matches shouldBe false
        config.match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe true
    }

    @Test
    fun `path criteria STARTS - basic`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc", "def"), mode = Mode.START, allowPartial = false)
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("123", "def"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc", "def"), mode = Mode.START, allowPartial = false)
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = Mode.START, allowPartial = false)
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
    }

    @Test
    fun `path criteria STARTS - partial`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc", ""), mode = Mode.START, allowPartial = false)
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc", ""), mode = Mode.START, allowPartial = false)
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "/"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = Mode.START, allowPartial = false)
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", ""))).matches shouldBe true

        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = Mode.START, allowPartial = false)
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abcdef"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = Mode.START, allowPartial = true)
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abcdef"))).matches shouldBe true
    }

    @Test
    fun `path criteria STARTS - casing`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "def"),
                    mode = Mode.START,
                    allowPartial = false,
                    ignoreCase = false
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "def"),
                    mode = Mode.START,
                    allowPartial = false,
                    ignoreCase = true
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "def"),
                    mode = Mode.START,
                    allowPartial = true,
                    ignoreCase = true
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF123"))).matches shouldBe true
    }

    @Test
    fun `path criteria CONTAINS - basic`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "def"),
                    mode = Mode.CONTAIN,
                    allowPartial = false,
                    ignoreCase = false
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "def"),
                    mode = Mode.CONTAIN,
                    allowPartial = false,
                    ignoreCase = false
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "d"),
                    mode = Mode.CONTAIN,
                    allowPartial = false,
                    ignoreCase = false
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe false
    }

    @Test
    fun `path criteria CONTAINS - partial`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "def"),
                    mode = Mode.CONTAIN,
                    allowPartial = true,
                    ignoreCase = false
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "d"),
                    mode = Mode.CONTAIN,
                    allowPartial = true,
                    ignoreCase = false
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc"),
                    mode = Mode.CONTAIN,
                    allowPartial = true,
                    ignoreCase = false
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("123"))).matches shouldBe false
    }

    @Test
    fun `path criteria CONTAINS - casing`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "def"),
                    mode = Mode.CONTAIN,
                    allowPartial = false,
                    ignoreCase = false
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "def"),
                    mode = Mode.CONTAIN,
                    allowPartial = false,
                    ignoreCase = true
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF"))).matches shouldBe true
    }

    @Test
    fun `path criteria ENDS - basic`() = runTest {
//        Config(
//            pathCriteria = setOf(
//                SegmentCriterium(
//                    segs("", "sdcard", "abc", "def"),
//                    mode = Mode.ENDS,
//                    allowPartial = false,
//                    ignoreCase = false
//                )
//            )
//        ).match(baseLookup.copy(lookedUp = basePath.child("abc","def"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "def"),
                    mode = Mode.END,
                    allowPartial = false,
                    ignoreCase = false
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("abc", "123"),
                    mode = Mode.END,
                    allowPartial = false,
                    ignoreCase = false
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "123"))).matches shouldBe true
    }

    @Test
    fun `path criteria ENDS - partial`() {
        TODO()
        // partial too
    }

    @Test
    fun `path criteria ENDS - casing`() {
        TODO()
        // partial too
    }

    @Test
    fun `path criteria MATCHES - basic`() {
        TODO()
    }

    @Test
    fun `path criteria MATCHES - partial`() {
        TODO()
    }

    @Test
    fun `path criteria MATCHES - casing`() {
        TODO()
    }

    @Test
    fun `pfp criteria STARTS - basic`() {
        TODO()
        // partial too
    }

    @Test
    fun `pfp criteria STARTS - partial`() {
        TODO()
        // partial too
    }

    @Test
    fun `pfp criteria STARTS - casing`() {
        TODO()
        // partial too
    }

    @Test
    fun `pfp criteria CONTAINS - basic`() {
        TODO()
        // partial too
    }

    @Test
    fun `pfp criteria CONTAINS - partial`() {
        TODO()
        // partial too
    }

    @Test
    fun `pfp criteria CONTAINS - casing`() {
        TODO()
        // partial too
    }

    @Test
    fun `pfp criteria ENDS - basic`() {
        TODO()
        // partial too
    }

    @Test
    fun `pfp criteria ENDS - partial`() {
        TODO()
        // partial too
    }

    @Test
    fun `pfp criteria ENDS - casing`() {
        TODO()
        // partial too
    }

    @Test
    fun `pfp criteria MATCHES - basic`() {
        TODO()
    }

    @Test
    fun `pfp criteria MATCHES - partial`() {
        TODO()
    }

    @Test
    fun `pfp criteria MATCHES - casing`() {
        TODO()
    }


    @Test
    fun `exclusion criteria STARTS - basic`() {
        TODO()
        // partial too
    }

    @Test
    fun `exclusion criteria STARTS - partial`() {
        TODO()
        // partial too
    }

    @Test
    fun `exclusion criteria STARTS - casing`() {
        TODO()
        // partial too
    }

    @Test
    fun `exclusion criteria CONTAINS - basic`() {
        TODO()
        // partial too
    }

    @Test
    fun `exclusion criteria CONTAINS - partial`() {
        TODO()
        // partial too
    }

    @Test
    fun `exclusion criteria CONTAINS - casing`() {
        TODO()
        // partial too
    }

    @Test
    fun `exclusion criteria ENDS - basic`() {
        TODO()
        // partial too
    }

    @Test
    fun `exclusion criteria ENDS - partial`() {
        TODO()
        // partial too
    }

    @Test
    fun `exclusion criteria ENDS - casing`() {
        TODO()
        // partial too
    }

    @Test
    fun `exclusion criteria MATCHES - basic`() {
        TODO()
    }

    @Test
    fun `exclusion criteria MATCHES - partial`() {
        TODO()
    }

    @Test
    fun `exclusion criteria MATCHES - casing`() {
        TODO()
    }

    @Test
    fun `name criteria STARTS - basic`() {
        TODO()
    }

    @Test
    fun `name criteria STARTS - casing`() {
        TODO()
    }

    @Test
    fun `name criteria CONTAINS - basic`() {
        TODO()
    }

    @Test
    fun `name criteria CONTAINS - casing`() {
        TODO()
    }

    @Test
    fun `name criteria ENDS - basic`() {
        TODO()
    }

    @Test
    fun `name criteria ENDS - casing`() {
        TODO()
    }

    @Test
    fun `name criteria MATCHES - basic`() {
        TODO()
    }

    @Test
    fun `name criteria MATCHES - casing`() {
        TODO()
    }
}