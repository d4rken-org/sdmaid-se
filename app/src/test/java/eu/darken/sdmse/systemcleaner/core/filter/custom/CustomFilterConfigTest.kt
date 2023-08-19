package eu.darken.sdmse.systemcleaner.core.filter.custom

import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.serialization.SerializationAppModule
import eu.darken.sdmse.systemcleaner.core.sieve.NameCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.time.Instant

class CustomFilterConfigTest : BaseTest() {
    private val moshi = SerializationAppModule().moshi().newBuilder().apply {
        add(NameCriterium.MOSHI_ADAPTER_FACTORY)
        add(SegmentCriterium.MOSHI_ADAPTER_FACTORY)
    }.build()

    private val adapter = moshi.adapter(CustomFilterConfig::class.java)

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
        val rawJson = adapter.toJson(original)
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
        adapter.fromJson(rawJson) shouldBe original
    }
}