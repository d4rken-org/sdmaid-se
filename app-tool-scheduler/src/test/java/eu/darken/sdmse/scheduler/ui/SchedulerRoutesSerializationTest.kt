package eu.darken.sdmse.scheduler.ui

import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableKotlinxJson

class SchedulerRoutesSerializationTest : BaseTest() {

    private val json = Json

    @Test
    fun `ScheduleItemRoute serialization`() {
        val original = ScheduleItemRoute(scheduleId = "schedule-123")

        val serialized = json.encodeToString(original)
        serialized.toComparableKotlinxJson() shouldBe """
            {
                "scheduleId": "schedule-123"
            }
        """.toComparableKotlinxJson()

        val deserialized = json.decodeFromString<ScheduleItemRoute>(serialized)
        deserialized shouldBe original
    }
}
