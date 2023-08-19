package eu.darken.sdmse.systemcleaner.core.filter.custom

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.serialization.SerializationAppModule
import eu.darken.sdmse.systemcleaner.core.sieve.NameCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class LegacyFilterSupportTest : BaseTest() {

    private fun create() = LegacyFilterSupport(
        moshi = SerializationAppModule().moshi()
    )

    @Test
    fun `import SD Maid 1 legacy v5 filter`() = runTest {
        val rawLegacyFilter = """
            {
                "color": "#03a9f4",
                "description": "5 items @ /storage/emulated/0",
                "exclusions": [
                    "backup"
                ],
                "identifier": "91c26321f304.scuf.sdm",
                "isEmpty": false,
                "label": "Legacy Test Filter",
                "locations": [
                    "SDCARD", "PORTABLE"
                ],
                "mainPath": [
                    "/storage/emulated/0/Bpics/",
                    "/storage/emulated/0/atest.json",
                    "/storage/emulated/0/eu.darken.sdmse-test-area-access",
                    "/storage/emulated/0/eu.darken.sdmse-test-area-access"
                ],
                "maximumAge": 999999,
                "maximumSize": 9001,
                "minimumAge": 333333,
                "minimumSize": 13,
                "pathContains": [
                    "atest"
                ],
                "possibleNameEndings": [
                    ".json",
                    "5"
                ],
                "possibleNameInits": [
                    "a",
                    "e"
                ],
                "regexes": [
                    ".+"
                ],
                "targetType": "FILE",
                "version": 4
            }
        """.trimIndent()


        val importedFilter = create().import(rawLegacyFilter)
        importedFilter shouldNotBe null
        importedFilter!!.apply {
            label shouldBe "Legacy Test Filter"
            areas shouldBe setOf(DataArea.Type.SDCARD, DataArea.Type.PORTABLE)
            pathCriteria shouldBe setOf(
                SegmentCriterium(
                    segments = segs("", "storage", "emulated", "0", "Bpics", ""),
                    mode = SegmentCriterium.Mode.Start(allowPartial = true, ignoreCase = true),
                ),
                SegmentCriterium(
                    segments = segs("", "storage", "emulated", "0", "atest.json"),
                    mode = SegmentCriterium.Mode.Start(allowPartial = true, ignoreCase = true),
                ),
                SegmentCriterium(
                    segments = segs("", "storage", "emulated", "0", "eu.darken.sdmse-test-area-access"),
                    mode = SegmentCriterium.Mode.Start(allowPartial = true, ignoreCase = true),
                ),
                SegmentCriterium(
                    segments = segs("", "storage", "emulated", "0", "eu.darken.sdmse-test-area-access"),
                    mode = SegmentCriterium.Mode.Start(allowPartial = true, ignoreCase = true),
                ),
                SegmentCriterium(
                    segments = segs("atest"),
                    mode = SegmentCriterium.Mode.Contain(allowPartial = true, ignoreCase = true),
                ),
            )
            nameCriteria shouldBe setOf(
                NameCriterium(
                    name = ".json",
                    mode = NameCriterium.Mode.End(ignoreCase = true),
                ),
                NameCriterium(
                    name = "5",
                    mode = NameCriterium.Mode.End(ignoreCase = true),
                ),
                NameCriterium(
                    name = "a",
                    mode = NameCriterium.Mode.Start(ignoreCase = true),
                ),
                NameCriterium(
                    name = "e",
                    mode = NameCriterium.Mode.Start(ignoreCase = true),
                ),
            )

            fileTypes shouldBe setOf(FileType.FILE)
            exclusionCriteria shouldBe setOf(
                SegmentCriterium(
                    segments = segs("backup"),
                    mode = SegmentCriterium.Mode.Contain(allowPartial = true, ignoreCase = true),
                ),
            )
        }
    }
}