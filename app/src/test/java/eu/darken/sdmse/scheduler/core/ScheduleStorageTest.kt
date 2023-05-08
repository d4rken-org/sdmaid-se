package eu.darken.sdmse.scheduler.core

import android.content.Context
import eu.darken.sdmse.common.files.local.deleteAll
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
    fun `load save clear`() = runTest {
        val schedule = Schedule(
            id = "1234id",
            createdAt = Instant.EPOCH
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
                        "appcleanerEnabled": false
                    }
                ]
            """.toComparableJson()
            saveFile.delete()
            load() shouldBe null
        }
    }
}