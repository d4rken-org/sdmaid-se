package eu.darken.sdmse.systemcleaner.core.filter.custom

import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.serialization.SerializationIOModule
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.time.Instant

class CustomFilterConfigTest : BaseTest() {
    private val json = SerializationIOModule().json()

    @Test
    fun `config serialization`() {
        val original = CustomFilterConfig(
            identifier = "some-id",
            label = "My label",
            createdAt = Instant.parse("2023-08-14T19:55:42.921588Z"),
            modifiedAt = Instant.parse("2023-08-14T19:55:42.921591Z"),
            pathCriteria = setOf(
                SegmentCriterium(
                    segments = segs("path", "crit"),
                    mode = SegmentCriterium.Mode.Ancestor()
                )
            ),
            nameCriteria = setOf(
                NameCriterium(
                    name = "some.apk",
                    mode = NameCriterium.Mode.Equal(ignoreCase = false)
                )
            )
        )
        val rawJson = json.encodeToString(CustomFilterConfig.serializer(), original)
        rawJson.toComparableJson() shouldBe """
            {
                "configVersion": 6,
                "id": "some-id",
                "createdAt": "2023-08-14T19:55:42.921588Z",
                "modifiedAt": "2023-08-14T19:55:42.921591Z",
                "label": "My label",
                "pathCriteria": [
                    {
                        "segments": [
                            "path",
                            "crit"
                        ],
                        "mode": {
                            "type": "ANCESTOR",
                            "ignoreCase": true
                        }
                    }
                ],
                "nameCriteria": [
                    {
                        "name": "some.apk",
                        "mode": {
                            "type": "MATCH",
                            "ignoreCase": false
                        }
                    }
                ]
            }
        """.toComparableJson()
        json.decodeFromString(CustomFilterConfig.serializer(), rawJson) shouldBe original
    }
}
