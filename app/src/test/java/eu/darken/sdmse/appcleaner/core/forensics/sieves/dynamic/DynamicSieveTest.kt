package eu.darken.sdmse.appcleaner.core.forensics.sieves.dynamic

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DynamicSieveTest : BaseTest() {

    @BeforeEach
    fun setup() {

    }

    fun create(
        configs: Set<DynamicSieve.MatchConfig>
    ): DynamicSieve = DynamicSieve(configs)

    @Test fun `invalid empty file`() {
        shouldThrowAny {
            create(emptySet())
        }
    }

    @Test fun `invalid app filter`() {
        shouldThrowAny {
            create(setOf(DynamicSieve.MatchConfig()))
        }
    }

    @Test fun `location condition`() {
        val config = DynamicSieve.MatchConfig(
            areaTypes = setOf(DataArea.Type.SDCARD),
            contains = setOf("a/test/path")
        )

        create(setOf(config)).apply {
            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.PRIVATE_DATA,
                target = segs("a", "test", "path")
            ) shouldBe false

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("a", "test", "path")
            ) shouldBe true
        }
    }

    @Test fun testBadMatch() {
        val config = DynamicSieve.MatchConfig(
            areaTypes = setOf(DataArea.Type.SDCARD),
            contains = setOf("a/test/path")
        )

        create(setOf(config)).apply {
            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("a", "test", "path")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("another", "test", "path")
            ) shouldBe false
        }
    }

    @Test fun testCaseSensitivity() {
        val config = DynamicSieve.MatchConfig(
            areaTypes = setOf(DataArea.Type.SDCARD, DataArea.Type.PRIVATE_DATA),
            contains = setOf("a/test/path")
        )
        create(setOf(config)).apply {
            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("a", "test", "path")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("A", "test", "PATH")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.PRIVATE_DATA,
                target = segs("A", "test", "PATH")
            ) shouldBe false
        }
    }

    @Test fun `startsWith ie inclusive`() {
        val config = DynamicSieve.MatchConfig(
            areaTypes = setOf(DataArea.Type.SDCARD),
            startsWith = setOf("a/test/path")
        )

        create(setOf(config)).apply {
            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("a", "test", "path")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("a", "test", "path", "file")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("not", "a", "test", "path")
            ) shouldBe false
        }
    }

    @Test fun `ancestors ie exclusive`() {
        val config = DynamicSieve.MatchConfig(
            areaTypes = setOf(DataArea.Type.SDCARD),
            ancestors = setOf("a/test/path")
        )

        create(setOf(config)).apply {
            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("a", "test", "path")
            ) shouldBe false

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("a", "test", "path", "file")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("not", "a", "test", "path")
            ) shouldBe false
        }
    }

    @Test fun testContains() {
        val config = DynamicSieve.MatchConfig(
            areaTypes = setOf(DataArea.Type.SDCARD, DataArea.Type.PRIVATE_DATA, DataArea.Type.SYSTEM),
            contains = setOf("a/test/path")
        )

        create(setOf(config)).apply {
            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("a", "test", "path")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.PRIVATE_DATA,
                target = segs("a", "test", "path", "file")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SYSTEM,
                target = segs("aaa", "test", "pathhhh")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SYSTEM,
                target = segs("123")
            ) shouldBe false
        }
    }

    @Test fun testRegex() {
        val config = DynamicSieve.MatchConfig(
            areaTypes = setOf(DataArea.Type.SDCARD, DataArea.Type.PRIVATE_DATA, DataArea.Type.SYSTEM),
            contains = setOf("a/test/path"),
            patterns = setOf("^(?>a*\\/[0-9a-z-]+\\/pa.+)$")
        )

        create(setOf(config)).apply {
            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("a", "test", "path")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.PRIVATE_DATA,
                target = segs("a", "test", "path", "file")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SYSTEM,
                target = segs("aaa", "test", "pathhhh")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SYSTEM,
                target = segs("123")
            ) shouldBe false
        }
    }
}