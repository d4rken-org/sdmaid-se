package eu.darken.sdmse.appcleaner.core.forensics.sieves

import eu.darken.sdmse.common.areas.DataArea.Type
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.sieve.CriteriaOperator
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class DynamicAppSieve2Test : BaseTest() {

    @BeforeEach
    fun setup() {

    }

    fun create(
        configs: Set<DynamicAppSieve2.MatchConfig>
    ): DynamicAppSieve2 = DynamicAppSieve2(configs)

    private fun DynamicAppSieve2.matches(
        rawPkg: String,
        areaType: Type,
        rawSegs: String,
    ): Boolean {
        val pfpSegs = rawSegs.toSegs()
        return matches(
            pkgId = rawPkg.toPkgId(),
            target = LocalPathLookup(
                lookedUp = LocalPath.build("sdcard").child(*pfpSegs.toTypedArray()),
                fileType = FileType.FILE,
                size = 16,
                modifiedAt = Instant.EPOCH,
                target = null,
            ),
            areaType = areaType,
            pfpSegs = pfpSegs
        )
    }

    @Test fun `invalid empty file`() {
        shouldThrowAny {
            create(emptySet())
        }
    }

    @Test fun `invalid app filter`() {
        shouldThrowAny {
            create(setOf(DynamicAppSieve2.MatchConfig()))
        }
    }

    @Test fun `location condition`() {
        val config = DynamicAppSieve2.MatchConfig(
            areaTypes = setOf(Type.SDCARD),
            pfpCriteria = setOf(
                SegmentCriterium("a/test/path", SegmentCriterium.Mode.Contain())
            )
        )

        create(setOf(config)).apply {
            matches("any.pkg", Type.PRIVATE_DATA, "a/test/path") shouldBe false
            matches("any.pkg", Type.SDCARD, "a/test/path") shouldBe true
        }
    }

    @Test fun testBadMatch() {
        val config = DynamicAppSieve2.MatchConfig(
            areaTypes = setOf(Type.SDCARD),
            pfpCriteria = setOf(
                SegmentCriterium("a/test/path", SegmentCriterium.Mode.Contain())
            ),
        )

        create(setOf(config)).apply {
            matches("any.pkg", Type.SDCARD, "a/test/path") shouldBe true
            matches("any.pkg", Type.SDCARD, "another/test/path") shouldBe false
        }
    }

    @Test fun testCaseSensitivity() {
        val config = DynamicAppSieve2.MatchConfig(
            areaTypes = setOf(Type.SDCARD, Type.PRIVATE_DATA),
            pfpCriteria = setOf(
                SegmentCriterium("a/test/path", SegmentCriterium.Mode.Contain())
            ),
        )
        create(setOf(config)).apply {
            matches("any.pkg", Type.SDCARD, "a/test/path") shouldBe true
            matches("any.pkg", Type.SDCARD, "A/test/PATH") shouldBe true
        }
    }

    @Test fun `startsWith ie inclusive`() {
        val config = DynamicAppSieve2.MatchConfig(
            areaTypes = setOf(Type.SDCARD),
            pfpCriteria = setOf(
                SegmentCriterium("a/test/path", SegmentCriterium.Mode.Start())
            ),
        )

        create(setOf(config)).apply {
            matches("any.pkg", Type.SDCARD, "a/test/path") shouldBe true
            matches("any.pkg", Type.SDCARD, "a/test/path/file") shouldBe true
            matches("any.pkg", Type.SDCARD, "not/a/test/path") shouldBe false
        }
    }

    @Test fun `ancestors ie exclusive`() {
        val config = DynamicAppSieve2.MatchConfig(
            areaTypes = setOf(Type.SDCARD),
            pfpCriteria = setOf(
                SegmentCriterium("a/test/path", SegmentCriterium.Mode.Ancestor())
            ),
        )

        create(setOf(config)).apply {
            matches("any.pkg", Type.SDCARD, "a/test/path") shouldBe false
            matches("any.pkg", Type.SDCARD, "a/test/path/file") shouldBe true
            matches("any.pkg", Type.SDCARD, "not/a/test/path") shouldBe false
        }
    }

    @Test fun testContains() {
        val config = DynamicAppSieve2.MatchConfig(
            areaTypes = setOf(Type.SDCARD, Type.PRIVATE_DATA, Type.SYSTEM),
            pfpCriteria = setOf(
                SegmentCriterium("a/test/path", SegmentCriterium.Mode.Contain(allowPartial = true))
            ),
        )

        create(setOf(config)).apply {
            matches("any.pkg", Type.SDCARD, "a/test/path") shouldBe true
            matches("any.pkg", Type.PRIVATE_DATA, "a/test/path/file") shouldBe true
            matches("any.pkg", Type.SYSTEM, "aaa/test/pathhhh") shouldBe true
            matches("any.pkg", Type.SYSTEM, "123") shouldBe false
        }
    }

    @Test fun testRegex() {
        val config = DynamicAppSieve2.MatchConfig(
            areaTypes = setOf(Type.SDCARD, Type.PRIVATE_DATA, Type.SYSTEM),
            pfpCriteria = setOf(
                SegmentCriterium("a/test/path", SegmentCriterium.Mode.Contain(allowPartial = true))
            ),
            pfpRegexes = setOf(Regex("^(?>a*/[0-9a-z-]+/pa.+)$")),
        )

        create(setOf(config)).apply {
            matches("any.pkg", Type.SDCARD, "a/test/path") shouldBe true
            matches("any.pkg", Type.PRIVATE_DATA, "a/test/path/file") shouldBe true
            matches("any.pkg", Type.SYSTEM, "aaa/test/pathhhh") shouldBe true
            matches("any.pkg", Type.SYSTEM, "123") shouldBe false
        }
    }

    @Test fun `name criteria`() {
        val config = DynamicAppSieve2.MatchConfig(
            areaTypes = setOf(Type.SDCARD, Type.PRIVATE_DATA, Type.SYSTEM),
            pfpCriteria = setOf(
                NameCriterium("testname", NameCriterium.Mode.Equal()),
            ),
        )

        create(setOf(config)).apply {
            matches("any.pkg", Type.SDCARD, "abc/test/testname") shouldBe true
            matches("any.pkg", Type.SDCARD, "abc/test/testnam") shouldBe false
            matches("any.pkg", Type.SDCARD, "abc/testname/test") shouldBe false
        }
    }

    @Test fun `name exclusion`() {
        val config = DynamicAppSieve2.MatchConfig(
            areaTypes = setOf(Type.SDCARD, Type.PRIVATE_DATA, Type.SYSTEM),
            pfpCriteria = setOf(
                NameCriterium("test", NameCriterium.Mode.Contain()),
            ),
            pfpExclusions = setOf(
                NameCriterium("name", NameCriterium.Mode.Contain()),
            )
        )

        create(setOf(config)).apply {
            matches("any.pkg", Type.SDCARD, "abc/test") shouldBe true
            matches("any.pkg", Type.SDCARD, "abc/testname") shouldBe false
            matches("any.pkg", Type.SDCARD, "abc/testeman") shouldBe true
        }
    }

    @Test fun `and criteria operator`() {
        val config = DynamicAppSieve2.MatchConfig(
            areaTypes = setOf(Type.SDCARD, Type.PRIVATE_DATA, Type.SYSTEM),
            pfpCriteria = setOf(
                CriteriaOperator.And(
                    SegmentCriterium("test/path", SegmentCriterium.Mode.Contain()),
                    SegmentCriterium("abc", SegmentCriterium.Mode.Start()),
                )
            ),
        )

        create(setOf(config)).apply {
            matches("any.pkg", Type.SDCARD, "abc/test/path") shouldBe true
            matches("any.pkg", Type.SDCARD, "def/test/path") shouldBe false
            matches("any.pkg", Type.SDCARD, "abc/test") shouldBe false
            matches("any.pkg", Type.SDCARD, "bc/test/pat") shouldBe false
            matches("any.pkg", Type.SDCARD, "abc/folder/test/path") shouldBe true
        }
    }

    @Test fun `nested criteria operator`() {
        val config = DynamicAppSieve2.MatchConfig(
            areaTypes = setOf(Type.SDCARD),
            pfpCriteria = setOf(
                CriteriaOperator.And(
                    SegmentCriterium("test/path", SegmentCriterium.Mode.Ancestor()),
                    CriteriaOperator.Or(
                        SegmentCriterium("abc", SegmentCriterium.Mode.Specific(1, backwards = true)),
                        SegmentCriterium("def", SegmentCriterium.Mode.Specific(1, backwards = true)),
                    )
                )
            ),
        )

        create(setOf(config)).apply {
            matches("any.pkg", Type.SDCARD, "test/path/abc") shouldBe false
            matches("any.pkg", Type.SDCARD, "test/path/abc/file") shouldBe true
            matches("any.pkg", Type.SDCARD, "asd/path/abc/file") shouldBe false
            matches("any.pkg", Type.SDCARD, "test/path/def") shouldBe false
            matches("any.pkg", Type.SDCARD, "test/path/def/file") shouldBe true
            matches("any.pkg", Type.SDCARD, "asd/path/def/file") shouldBe false
            matches("any.pkg", Type.SDCARD, "test/path/ghi") shouldBe false
            matches("any.pkg", Type.SDCARD, "test/path/ghi/file") shouldBe false
        }
    }
}
