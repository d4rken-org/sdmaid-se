package eu.darken.sdmse.systemcleaner.core.filter

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve.Config
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve.TargetType
import eu.darken.sdmse.systemcleaner.core.sieve.NameCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
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
    fun `SegmentCriterium default values`() {
        SegmentCriterium.Mode.Ancestor().apply {
            ignoreCase shouldBe true
        }
        SegmentCriterium.Mode.Start().apply {
            allowPartial shouldBe false
            ignoreCase shouldBe true
        }
        SegmentCriterium.Mode.Contain().apply {
            allowPartial shouldBe false
            ignoreCase shouldBe true
        }
        SegmentCriterium.Mode.End().apply {
            allowPartial shouldBe false
            ignoreCase shouldBe true
        }
        SegmentCriterium.Mode.Equal().apply {
            ignoreCase shouldBe true
        }
    }

    @Test
    fun `NameCriterium default values`() {
        NameCriterium.Mode.Start().apply {
            ignoreCase shouldBe true
        }
        NameCriterium.Mode.Contain().apply {
            ignoreCase shouldBe true
        }
        NameCriterium.Mode.End().apply {
            ignoreCase shouldBe true
        }
        NameCriterium.Mode.Equal().apply {
            ignoreCase shouldBe true
        }
    }

    @Test
    fun `just regex`() = runTest {
        val config = Config(
            pathRegexes = setOf(Regex(".+/a.c/[0-9]+$"))
        )

        config.match(
            baseLookup.copy(lookedUp = basePath.child("/ac/123"))
        ).matches shouldBe false
        config.match(
            baseLookup.copy(lookedUp = basePath.child("/abc/123"))
        ).matches shouldBe true
    }

    @Test
    fun `path criteria ANCESTOR - basic`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = SegmentCriterium.Mode.Ancestor())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "ab"), mode = SegmentCriterium.Mode.Ancestor())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("sdcard", "abc"), mode = SegmentCriterium.Mode.Ancestor())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = SegmentCriterium.Mode.Ancestor())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("123", "def"))).matches shouldBe false
    }

    @Test
    fun `path criteria ANCESTOR - casing`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = SegmentCriterium.Mode.Ancestor(ignoreCase = false))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("ABC", "def"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = SegmentCriterium.Mode.Ancestor(ignoreCase = true))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("ABC", "def"))).matches shouldBe true
    }

    @Test
    fun `path criteria START - basic`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc", "def"), mode = SegmentCriterium.Mode.Start())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("123", "def"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc", "def"), mode = SegmentCriterium.Mode.Start())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = SegmentCriterium.Mode.Start())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
    }

    @Test
    fun `path criteria START - partial`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc", ""), mode = SegmentCriterium.Mode.Start(allowPartial = true))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", ""), mode = SegmentCriterium.Mode.Start(allowPartial = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "/"))).matches shouldBe true

        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = SegmentCriterium.Mode.Start(allowPartial = true))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", ""))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = SegmentCriterium.Mode.Start(allowPartial = false))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", ""))).matches shouldBe true

        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = SegmentCriterium.Mode.Start(allowPartial = false))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abcdef"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = SegmentCriterium.Mode.Start(allowPartial = true))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abcdef"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = SegmentCriterium.Mode.Start(allowPartial = false))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abcdef", "123"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = SegmentCriterium.Mode.Start(allowPartial = true))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abcdef", "123"))).matches shouldBe true
    }

    @Test
    fun `path criteria START - casing`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "def"),
                    mode = SegmentCriterium.Mode.Start(ignoreCase = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "def"),
                    mode = SegmentCriterium.Mode.Start(ignoreCase = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF"))).matches shouldBe true

        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "ABC"), mode = SegmentCriterium.Mode.Start(ignoreCase = false))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "ABC"), mode = SegmentCriterium.Mode.Start(ignoreCase = true))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF"))).matches shouldBe true
    }

    @Test
    fun `path criteria CONTAIN - basic`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = SegmentCriterium.Mode.Contain())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = SegmentCriterium.Mode.Contain())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("123"))).matches shouldBe false

        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("sdcard"), mode = SegmentCriterium.Mode.Contain())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("123"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs(""), mode = SegmentCriterium.Mode.Contain())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("123"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("123"), mode = SegmentCriterium.Mode.Contain())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("123"))).matches shouldBe true
    }

    @Test
    fun `path criteria CONTAIN - partial`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "ab"), mode = SegmentCriterium.Mode.Contain(allowPartial = false))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "ab"), mode = SegmentCriterium.Mode.Contain(allowPartial = true))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc"))).matches shouldBe true

        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sd"), mode = SegmentCriterium.Mode.Contain(allowPartial = false))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("ab"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sd"), mode = SegmentCriterium.Mode.Contain(allowPartial = true))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("ab"))).matches shouldBe true
    }

    @Test
    fun `path criteria CONTAIN - casing`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "def"),
                    mode = SegmentCriterium.Mode.Contain(ignoreCase = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("ABC", "def"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "def"),
                    mode = SegmentCriterium.Mode.Contain(ignoreCase = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("ABC", "def"))).matches shouldBe true

        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "ABC", "def"),
                    mode = SegmentCriterium.Mode.Contain(ignoreCase = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "ABC", "def"),
                    mode = SegmentCriterium.Mode.Contain(ignoreCase = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
    }

    @Test
    fun `path criteria END - basic`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("abc", "def"), mode = SegmentCriterium.Mode.End())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc", "def"), mode = SegmentCriterium.Mode.End())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc", "def"), mode = SegmentCriterium.Mode.End())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc"))).matches shouldBe false
    }

    @Test
    fun `path criteria END - partial`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("c", "def"), mode = SegmentCriterium.Mode.End(allowPartial = false))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("c", "def"), mode = SegmentCriterium.Mode.End(allowPartial = true))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("c", "def"), mode = SegmentCriterium.Mode.End(allowPartial = true))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "de"))).matches shouldBe false
    }

    @Test
    fun `path criteria END - casing`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("abc", "def"), mode = SegmentCriterium.Mode.End(ignoreCase = false))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("ABC", "def"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("abc", "def"), mode = SegmentCriterium.Mode.End(ignoreCase = true))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("ABC", "def"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("ABC", "def"), mode = SegmentCriterium.Mode.End(ignoreCase = true))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
    }

    @Test
    fun `path criteria MATCH - basic`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc", "def"), mode = SegmentCriterium.Mode.Equal())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc", "def"), mode = SegmentCriterium.Mode.Equal())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "de"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(segs("", "sdcard", "abc"), mode = SegmentCriterium.Mode.Equal())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe false
    }

    @Test
    fun `path criteria MATCH - casing`() = runTest {
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "def"),
                    mode = SegmentCriterium.Mode.Equal(ignoreCase = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("ABC", "def"))).matches shouldBe false
        Config(
            pathCriteria = setOf(
                SegmentCriterium(
                    segs("", "sdcard", "abc", "def"),
                    mode = SegmentCriterium.Mode.Equal(ignoreCase = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("ABC", "def"))).matches shouldBe true
    }

    @Test
    fun `pfp criteria ANCESTOR - basic`() = runTest {
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("def"),
                    mode = SegmentCriterium.Mode.Ancestor()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc"),
                    mode = SegmentCriterium.Mode.Ancestor()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc", "def"),
                    mode = SegmentCriterium.Mode.Ancestor()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe false
    }

    @Test
    fun `pfp criteria ANCESTOR - casing`() = runTest {
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc"),
                    mode = SegmentCriterium.Mode.Ancestor(ignoreCase = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("ABC", "def"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc"),
                    mode = SegmentCriterium.Mode.Ancestor(ignoreCase = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("ABC", "def"))).matches shouldBe true
    }

    @Test
    fun `pfp criteria START - basic`() = runTest {
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("def"),
                    mode = SegmentCriterium.Mode.Start()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc"),
                    mode = SegmentCriterium.Mode.Start()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
    }

    @Test
    fun `pfp criteria START - partial`() = runTest {
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("ab"),
                    mode = SegmentCriterium.Mode.Start(allowPartial = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("ab"),
                    mode = SegmentCriterium.Mode.Start(allowPartial = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc", "d"),
                    mode = SegmentCriterium.Mode.Start(allowPartial = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc", "d"),
                    mode = SegmentCriterium.Mode.Start(allowPartial = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
    }

    @Test
    fun `pfp criteria START - casing`() = runTest {
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc"),
                    mode = SegmentCriterium.Mode.Start(ignoreCase = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("ABC", "def"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc"),
                    mode = SegmentCriterium.Mode.Start(ignoreCase = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("ABC", "def"))).matches shouldBe true
    }

    @Test
    fun `pfp criteria CONTAIN - basic`() = runTest {
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("123"),
                    mode = SegmentCriterium.Mode.Contain()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc"),
                    mode = SegmentCriterium.Mode.Contain()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
    }

    @Test
    fun `pfp criteria CONTAIN - partial`() = runTest {
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("bc", "de"),
                    mode = SegmentCriterium.Mode.Contain(allowPartial = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("bc", "de"),
                    mode = SegmentCriterium.Mode.Contain(allowPartial = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
    }

    @Test
    fun `pfp criteria CONTAIN - casing`() = runTest {
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("def"),
                    mode = SegmentCriterium.Mode.Contain(ignoreCase = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF", "ghi"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("def"),
                    mode = SegmentCriterium.Mode.Contain(ignoreCase = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF", "ghi"))).matches shouldBe true
    }

    @Test
    fun `pfp criteria END - basic`() = runTest {
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc", "def"),
                    mode = SegmentCriterium.Mode.End()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("def", "ghi"),
                    mode = SegmentCriterium.Mode.End()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
    }

    @Test
    fun `pfp criteria END - partial`() = runTest {
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("ef", "ghi"),
                    mode = SegmentCriterium.Mode.End(allowPartial = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("ef", "ghi"),
                    mode = SegmentCriterium.Mode.End(allowPartial = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
    }

    @Test
    fun `pfp criteria END - casing`() = runTest {
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("def", "ghi"),
                    mode = SegmentCriterium.Mode.End(ignoreCase = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF", "ghi"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("def", "ghi"),
                    mode = SegmentCriterium.Mode.End(ignoreCase = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF", "ghi"))).matches shouldBe true
    }

    @Test
    fun `pfp criteria MATCH - basic`() = runTest {
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc", "def", ""),
                    mode = SegmentCriterium.Mode.Equal()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc", "def"),
                    mode = SegmentCriterium.Mode.Equal()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
    }

    @Test
    fun `pfp criteria MATCH - casing`() = runTest {
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc", "def"),
                    mode = SegmentCriterium.Mode.Equal(ignoreCase = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc", "def"),
                    mode = SegmentCriterium.Mode.Equal(ignoreCase = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF"))).matches shouldBe true
    }

    @Test
    fun `path exclusion criteria START - basic`() = runTest {
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("def"),
                    mode = SegmentCriterium.Mode.Start()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc"),
                    mode = SegmentCriterium.Mode.Start()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def"))).matches shouldBe true
    }

    @Test
    fun `path exclusion criteria START - partial`() = runTest {
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc", "d"),
                    mode = SegmentCriterium.Mode.Start(allowPartial = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("abc", "d"),
                    mode = SegmentCriterium.Mode.Start(allowPartial = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
    }

    @Test
    fun `path exclusion criteria START - casing`() = runTest {
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("ABC", "def"),
                    mode = SegmentCriterium.Mode.Start(ignoreCase = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
        Config(
            pfpCriteria = setOf(
                SegmentCriterium(
                    segs("ABC", "def"),
                    mode = SegmentCriterium.Mode.Start(ignoreCase = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
    }

    @Test
    fun `path exclusion criteria CONTAIN - basic`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain()))
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pathExclusions = setOf(
                SegmentCriterium(segs("sdcard"), mode = SegmentCriterium.Mode.Contain())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `path exclusion criteria CONTAIN - partial`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain()))
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pathExclusions = setOf(
                SegmentCriterium(segs("sdcard", "ab"), mode = SegmentCriterium.Mode.Contain(allowPartial = false))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pathExclusions = setOf(
                SegmentCriterium(segs("sdcard", "ab"), mode = SegmentCriterium.Mode.Contain(allowPartial = true))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `path exclusion criteria CONTAIN - casing`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pathExclusions = setOf(
                SegmentCriterium(segs("sdcard", "abc"), mode = SegmentCriterium.Mode.Contain(ignoreCase = false))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("ABC", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pathExclusions = setOf(
                SegmentCriterium(segs("sdcard", "abc"), mode = SegmentCriterium.Mode.Contain(ignoreCase = true))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("ABC", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `path exclusion criteria END - basic`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pathExclusions = setOf(
                SegmentCriterium(segs("sdcard", "abc", "def"), mode = SegmentCriterium.Mode.End())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pathExclusions = setOf(
                SegmentCriterium(segs("sdcard", "abc", "def", "ghi"), mode = SegmentCriterium.Mode.End())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `path exclusion criteria END - partial`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pathExclusions = setOf(
                SegmentCriterium(segs("ard", "abc", "def"), mode = SegmentCriterium.Mode.End(allowPartial = false))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pathExclusions = setOf(
                SegmentCriterium(
                    segs("card", "abc", "def", "ghi"),
                    mode = SegmentCriterium.Mode.End(allowPartial = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `path exclusion criteria END - casing`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pathExclusions = setOf(
                SegmentCriterium(segs("SDCARD", "abc", "def"), mode = SegmentCriterium.Mode.End(ignoreCase = false))
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pathExclusions = setOf(
                SegmentCriterium(
                    segs("SDCARD", "abc", "def", "ghi"),
                    mode = SegmentCriterium.Mode.End(ignoreCase = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `path exclusion criteria MATCH - basic`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pathExclusions = setOf(
                SegmentCriterium(segs("sdcard", "abc", "def"), mode = SegmentCriterium.Mode.Equal())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pathExclusions = setOf(
                SegmentCriterium(segs("", "sdcard", "abc", "def", "ghi"), mode = SegmentCriterium.Mode.Equal())
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `path exclusion criteria MATCH - casing`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pathExclusions = setOf(
                SegmentCriterium(
                    segs("", "SDCARD", "abc", "def"),
                    mode = SegmentCriterium.Mode.Equal(ignoreCase = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pathExclusions = setOf(
                SegmentCriterium(
                    segs("", "SDCARD", "abc", "def", "ghi"),
                    mode = SegmentCriterium.Mode.Equal(ignoreCase = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `pfp exclusion criteria START - basic`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("def"),
                    mode = SegmentCriterium.Mode.Start()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("abc"),
                    mode = SegmentCriterium.Mode.Start()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `pfp exclusion criteria START - partial`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("abc", "de"),
                    mode = SegmentCriterium.Mode.Start(allowPartial = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("abc", "de"),
                    mode = SegmentCriterium.Mode.Start(allowPartial = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `pfp exclusion criteria START - casing`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("abc", "DEF"),
                    mode = SegmentCriterium.Mode.Start(ignoreCase = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("abc", "DEF"),
                    mode = SegmentCriterium.Mode.Start(ignoreCase = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `pfp exclusion criteria CONTAIN - basic`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("sdcard"),
                    mode = SegmentCriterium.Mode.Contain()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("def"),
                    mode = SegmentCriterium.Mode.Contain()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `pfp exclusion criteria CONTAIN - partial`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("c", "def", "g"),
                    mode = SegmentCriterium.Mode.Contain(allowPartial = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("c", "def", "g"),
                    mode = SegmentCriterium.Mode.Contain(allowPartial = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `pfp exclusion criteria CONTAIN - casing`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("def"),
                    mode = SegmentCriterium.Mode.Contain(ignoreCase = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("def"),
                    mode = SegmentCriterium.Mode.Contain(ignoreCase = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "DEF", "ghi"))).matches shouldBe false
    }

    @Test
    fun `pfp exclusion criteria END - basic`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("abc"),
                    mode = SegmentCriterium.Mode.End()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("def", "ghi"),
                    mode = SegmentCriterium.Mode.End()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `pfp exclusion criteria END - partial`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("ef", "ghi"),
                    mode = SegmentCriterium.Mode.End(allowPartial = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("ef", "ghi"),
                    mode = SegmentCriterium.Mode.End(allowPartial = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `pfp exclusion criteria END - casing`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("DEF", "ghi"),
                    mode = SegmentCriterium.Mode.End(ignoreCase = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("DEF", "ghi"),
                    mode = SegmentCriterium.Mode.End(ignoreCase = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `pfp exclusion criteria MATCH - basic`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("def", "ghi"),
                    mode = SegmentCriterium.Mode.Equal()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("abc", "def", "ghi"),
                    mode = SegmentCriterium.Mode.Equal()
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `pfp exclusion criteria MATCH - casing`() = runTest {
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("ABC", "def", "GHI"),
                    mode = SegmentCriterium.Mode.Equal(ignoreCase = false)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
        Config(
            pathCriteria = setOf(SegmentCriterium(segs("abc"), mode = SegmentCriterium.Mode.Contain())),
            pfpExclusions = setOf(
                SegmentCriterium(
                    segs("ABC", "def", "GHI"),
                    mode = SegmentCriterium.Mode.Equal(ignoreCase = true)
                )
            )
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
    }

    @Test
    fun `name criteria START - basic`() = runTest {
        Config(
            nameCriteria = setOf(NameCriterium("abc", mode = NameCriterium.Mode.Start())),
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
        Config(
            nameCriteria = setOf(NameCriterium("ghi", mode = NameCriterium.Mode.Start())),
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
    }

    @Test
    fun `name criteria START - casing`() = runTest {
        Config(
            nameCriteria = setOf(NameCriterium("ghi", mode = NameCriterium.Mode.Start(ignoreCase = false))),
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "GHI"))).matches shouldBe false
        Config(
            nameCriteria = setOf(NameCriterium("ghi", mode = NameCriterium.Mode.Start(ignoreCase = true))),
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
    }

    @Test
    fun `name criteria CONTAIN - basic`() = runTest {
        Config(
            nameCriteria = setOf(NameCriterium("e", mode = NameCriterium.Mode.Contain())),
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
        Config(
            nameCriteria = setOf(NameCriterium("h", mode = NameCriterium.Mode.Contain())),
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
    }

    @Test
    fun `name criteria CONTAIN - casing`() = runTest {
        Config(
            nameCriteria = setOf(NameCriterium("h", mode = NameCriterium.Mode.Contain(ignoreCase = false))),
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "GHI"))).matches shouldBe false
        Config(
            nameCriteria = setOf(NameCriterium("h", mode = NameCriterium.Mode.Contain(ignoreCase = true))),
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "GHI"))).matches shouldBe true
    }

    @Test
    fun `name criteria END - basic`() = runTest {
        Config(
            nameCriteria = setOf(NameCriterium("h", mode = NameCriterium.Mode.End())),
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
        Config(
            nameCriteria = setOf(NameCriterium("hi", mode = NameCriterium.Mode.End())),
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
    }

    @Test
    fun `name criteria END - casing`() = runTest {
        Config(
            nameCriteria = setOf(NameCriterium("h", mode = NameCriterium.Mode.End(ignoreCase = false))),
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "gHI"))).matches shouldBe false
        Config(
            nameCriteria = setOf(NameCriterium("hi", mode = NameCriterium.Mode.End(ignoreCase = true))),
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "gHI"))).matches shouldBe true
    }

    @Test
    fun `name criteria MATCH - basic`() = runTest {
        Config(
            nameCriteria = setOf(NameCriterium("def", mode = NameCriterium.Mode.End())),
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe false
        Config(
            nameCriteria = setOf(NameCriterium("ghi", mode = NameCriterium.Mode.End())),
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "ghi"))).matches shouldBe true
    }

    @Test
    fun `name criteria MATCH - casing`() = runTest {
        Config(
            nameCriteria = setOf(NameCriterium("ghi", mode = NameCriterium.Mode.End(ignoreCase = false))),
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "GHI"))).matches shouldBe false
        Config(
            nameCriteria = setOf(NameCriterium("ghi", mode = NameCriterium.Mode.End(ignoreCase = true))),
        ).match(baseLookup.copy(lookedUp = basePath.child("abc", "def", "GHI"))).matches shouldBe true
    }
}