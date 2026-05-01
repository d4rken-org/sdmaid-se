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
    fun `SchedulerSettingsRoute serialization`() {
        val serialized = json.encodeToString(SchedulerSettingsRoute.serializer(), SchedulerSettingsRoute)
        serialized.toComparableKotlinxJson() shouldBe "{}".toComparableKotlinxJson()

        val deserialized = json.decodeFromString(SchedulerSettingsRoute.serializer(), serialized)
        deserialized shouldBe SchedulerSettingsRoute
    }

    @Test
    fun `SchedulerManagerRoute serialization`() {
        val serialized = json.encodeToString(SchedulerManagerRoute.serializer(), SchedulerManagerRoute)
        serialized.toComparableKotlinxJson() shouldBe "{}".toComparableKotlinxJson()

        val deserialized = json.decodeFromString(SchedulerManagerRoute.serializer(), serialized)
        deserialized shouldBe SchedulerManagerRoute
    }

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
