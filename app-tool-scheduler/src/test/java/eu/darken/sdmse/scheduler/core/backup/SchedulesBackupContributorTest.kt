package eu.darken.sdmse.scheduler.core.backup

import eu.darken.sdmse.common.backup.RestoreMode
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.ScheduleStorage
import eu.darken.sdmse.scheduler.core.SchedulerManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration

class SchedulesBackupContributorTest : BaseTest() {

    private val storage = mockk<ScheduleStorage>()
    private val manager = mockk<SchedulerManager>(relaxed = true)
    private val json = Json
    private val serializer = SetSerializer(Schedule.serializer())
    private val contributor = SchedulesBackupContributor(storage, manager, json)

    @Test
    fun `merge saves each schedule via the manager and keeps existing`() = runTest {
        val data = json.encodeToJsonElement(serializer, setOf(Schedule(id = "s1"), Schedule(id = "s2")))

        contributor.restore(data, RestoreMode.MERGE)

        coVerify { manager.saveSchedule(match { it.id == "s1" }) }
        coVerify { manager.saveSchedule(match { it.id == "s2" }) }
        coVerify(exactly = 0) { manager.removeSchedule(any()) }
        coVerify(exactly = 0) { storage.load() }
    }

    @Test
    fun `replace swaps the whole schedule set atomically`() = runTest {
        val data = json.encodeToJsonElement(serializer, setOf(Schedule(id = "s1")))

        contributor.restore(data, RestoreMode.REPLACE)

        coVerify { manager.setSchedules(match { set -> set.single().id == "s1" }) }
        coVerify(exactly = 0) { manager.removeSchedule(any()) }
        coVerify(exactly = 0) { manager.saveSchedule(any()) }
    }

    @Test
    fun `restore clamps sub-day repeat intervals to one day`() = runTest {
        val data = json.encodeToJsonElement(
            serializer,
            setOf(Schedule(id = "s1", repeatInterval = Duration.ofHours(1))),
        )

        contributor.restore(data, RestoreMode.REPLACE)

        coVerify { manager.setSchedules(match { it.single().repeatInterval == Duration.ofDays(1) }) }
    }

    @Test
    fun `restore clamps negative repeat intervals during merge too`() = runTest {
        val data = json.encodeToJsonElement(
            serializer,
            setOf(Schedule(id = "s1", repeatInterval = Duration.ofDays(-3))),
        )

        contributor.restore(data, RestoreMode.MERGE)

        coVerify { manager.saveSchedule(match { it.repeatInterval == Duration.ofDays(1) }) }
    }

    @Test
    fun `validate decodes the section and rejects garbage`() = runTest {
        contributor.validate(json.encodeToJsonElement(serializer, setOf(Schedule(id = "s1"))))

        shouldThrow<Exception> { contributor.validate(JsonPrimitive("not a schedule set")) }
    }

    @Test
    fun `snapshot serializes current schedules`() = runTest {
        coEvery { storage.load() } returns setOf(Schedule(id = "s1"))

        val snap = contributor.snapshot()!!

        json.decodeFromJsonElement(serializer, snap).map { it.id } shouldBe listOf("s1")
    }

    @Test
    fun `snapshot is null when there are no schedules`() = runTest {
        coEvery { storage.load() } returns emptySet()
        contributor.snapshot() shouldBe null
    }
}
