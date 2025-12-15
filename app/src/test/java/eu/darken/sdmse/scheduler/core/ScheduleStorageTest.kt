package eu.darken.sdmse.scheduler.core

import android.content.Context
import eu.darken.sdmse.common.files.core.local.deleteAll
import eu.darken.sdmse.common.serialization.SerializationAppModule
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.json.toComparableJson
import java.io.File
import java.time.Duration
import java.time.Instant

class ScheduleStorageTest : BaseTest() {
    private val testDir = File(IO_TEST_BASEDIR)
    private val context: Context = mockk<Context>().apply {
        every { filesDir } returns testDir
    }

    @AfterEach
    fun cleanup() {
        testDir.deleteAll()
    }

    private fun create() = ScheduleStorage(
        context = context,
        dispatcherProvider = TestDispatcherProvider(),
        moshi = SerializationAppModule().moshi()
    )

    @Test
    fun `load empty`() = runTest {
        create().load() shouldBe null
    }

    @Test
    fun `serialization - minimal data`() = runTest {
        val schedule = Schedule(
            id = "1234id",
            createdAt = Instant.EPOCH,
        )
        create().apply {
            load() shouldBe null
            save(setOf(schedule))
            load() shouldBe setOf(schedule)
            val saveFile = provideBackupPath().listFiles()!!.single()
            saveFile.readText().toComparableJson() shouldBe """
                [
                    {
                        "id": "1234id",
                        "createdAt": "1970-01-01T00:00:00Z",
                        "hour": 22.0,
                        "minute": 0.0,
                        "label": "",
                        "repeatInterval": "PT72H",
                        "corpsefinderEnabled": false,
                        "systemcleanerEnabled": false,
                        "appcleanerEnabled": false,
                        "commandsAfterSchedule": []
                    }
                ]
            """.toComparableJson()
            saveFile.delete()
            load() shouldBe null
        }
    }

    @Test
    fun `serialization - full data`() = runTest {
        val schedule = Schedule(
            id = "full-id",
            createdAt = Instant.parse("2024-01-15T10:00:00Z"),
            scheduledAt = Instant.parse("2024-01-15T12:00:00Z"),
            hour = 22,
            minute = 30,
            label = "Nightly Cleanup",
            repeatInterval = Duration.ofDays(1),
            userZone = "Europe/Berlin",
            useCorpseFinder = true,
            useSystemCleaner = true,
            useAppCleaner = true,
            commandsAfterSchedule = listOf("reboot"),
            executedAt = Instant.parse("2024-01-14T21:30:00Z"),
        )
        create().apply {
            save(setOf(schedule))
            load() shouldBe setOf(schedule)
            val saveFile = provideBackupPath().listFiles()!!.single()
            saveFile.readText().toComparableJson() shouldBe """
                [
                    {
                        "id": "full-id",
                        "createdAt": "2024-01-15T10:00:00Z",
                        "scheduledAt": "2024-01-15T12:00:00Z",
                        "hour": 22.0,
                        "minute": 30.0,
                        "label": "Nightly Cleanup",
                        "repeatInterval": "PT24H",
                        "userZone": "Europe/Berlin",
                        "corpsefinderEnabled": true,
                        "systemcleanerEnabled": true,
                        "appcleanerEnabled": true,
                        "commandsAfterSchedule": ["reboot"],
                        "executedAt": "2024-01-14T21:30:00Z"
                    }
                ]
            """.toComparableJson()
        }
    }
}