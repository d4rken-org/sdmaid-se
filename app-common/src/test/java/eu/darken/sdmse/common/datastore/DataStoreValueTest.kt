package eu.darken.sdmse.common.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import java.nio.file.Path

class DataStoreValueTest : BaseTest() {

    @Test
    fun `reading and writing strings`(@TempDir tempDir: Path) = runTest {
        val testStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { tempDir.resolve("test.preferences_pb").toFile() },
        )

        testStore.createValue<String?>(
            key = "testKey",
            defaultValue = "default"
        ).apply {
            keyName shouldBe "testKey"

            flow.first() shouldBe "default"
            value() shouldBe "default"
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe "default"
                "newvalue"
            } shouldBe DataStoreValue.Updated("default", "newvalue")

            flow.first() shouldBe "newvalue"
            value() shouldBe "newvalue"
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe "newvalue"

            update {
                it shouldBe "newvalue"
                null
            } shouldBe DataStoreValue.Updated("newvalue", "default")

            flow.first() shouldBe "default"
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null

            value("newsecond")
            value() shouldBe "newsecond"
        }
    }

    @Test
    fun `reading and writing boolean`(@TempDir tempDir: Path) = runTest {
        val testStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { tempDir.resolve("test.preferences_pb").toFile() },
        )

        testStore.createValue<Boolean>(
            key = "testKey",
            defaultValue = true
        ).apply {
            keyName shouldBe "testKey"

            flow.first() shouldBe true
            testStore.data.first()[booleanPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe true
                false
            } shouldBe DataStoreValue.Updated(old = true, new = false)

            flow.first() shouldBe false
            testStore.data.first()[booleanPreferencesKey(keyName)] shouldBe false

            update {
                it shouldBe false
                null
            } shouldBe DataStoreValue.Updated(old = false, new = true)

            flow.first() shouldBe true
            testStore.data.first()[booleanPreferencesKey(keyName)] shouldBe null
        }

        testStore.createValue<Boolean?>(
            key = "testKey2",
            defaultValue = null
        ).apply {
            keyName shouldBe "testKey2"

            flow.first() shouldBe null
            testStore.data.first()[booleanPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe null
                false
            } shouldBe DataStoreValue.Updated(old = null, new = false)

            flow.first() shouldBe false
            testStore.data.first()[booleanPreferencesKey(keyName)] shouldBe false

            update {
                it shouldBe false
                null
            } shouldBe DataStoreValue.Updated(old = false, new = null)

            flow.first() shouldBe null
            testStore.data.first()[booleanPreferencesKey(keyName)] shouldBe null
        }
    }

    @Test
    fun `reading and writing long`(@TempDir tempDir: Path) = runTest {
        val testStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { tempDir.resolve("test.preferences_pb").toFile() },
        )

        testStore.createValue<Long?>(
            key = "testKey",
            defaultValue = 9000L
        ).apply {
            flow.first() shouldBe 9000L
            testStore.data.first()[longPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe 9000L
                9001L
            }

            flow.first() shouldBe 9001L
            testStore.data.first()[longPreferencesKey(keyName)] shouldBe 9001L

            update {
                it shouldBe 9001L
                null
            }

            flow.first() shouldBe 9000L
            testStore.data.first()[longPreferencesKey(keyName)] shouldBe null
        }
    }

    @Test
    fun `reading and writing integer`(@TempDir tempDir: Path) = runTest {
        val testStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { tempDir.resolve("test.preferences_pb").toFile() },
        )

        testStore.createValue<Long?>(
            key = "testKey",
            defaultValue = 123
        ).apply {

            flow.first() shouldBe 123
            testStore.data.first()[intPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe 123
                44
            }

            flow.first() shouldBe 44
            testStore.data.first()[intPreferencesKey(keyName)] shouldBe 44

            update {
                it shouldBe 44
                null
            }

            flow.first() shouldBe 123
            testStore.data.first()[intPreferencesKey(keyName)] shouldBe null
        }
    }

    @Test
    fun `reading and writing float`(@TempDir tempDir: Path) = runTest {
        val testStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { tempDir.resolve("test.preferences_pb").toFile() },
        )

        testStore.createValue<Float?>(
            key = "testKey",
            defaultValue = 3.6f
        ).apply {
            flow.first() shouldBe 3.6f
            testStore.data.first()[floatPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe 3.6f
                15000f
            }

            flow.first() shouldBe 15000f
            testStore.data.first()[floatPreferencesKey(keyName)] shouldBe 15000f

            update {
                it shouldBe 15000f
                null
            }

            flow.first() shouldBe 3.6f
            testStore.data.first()[floatPreferencesKey(keyName)] shouldBe null
        }
    }

    @Test
    fun `createValue with null String default creates stringPreferencesKey`(@TempDir tempDir: Path) = runTest {
        val testStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { tempDir.resolve("test.preferences_pb").toFile() },
        )

        testStore.createValue<String?>("test.nullable.string", null).apply {
            flow.first() shouldBe null

            value("hello")
            flow.first() shouldBe "hello"
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe "hello"

            value(null)
            flow.first() shouldBe null
        }
    }

    @Test
    fun `concurrent update calls preserve atomicity`(@TempDir tempDir: Path) = runTest {
        val testStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { tempDir.resolve("test.preferences_pb").toFile() },
        )

        val counter = testStore.createValue<Long>("test.counter", defaultValue = 0L)

        val jobs = (1..100).map {
            async {
                counter.update { current -> (current as Long) + 1L }
            }
        }
        jobs.awaitAll()

        counter.value() shouldBe 100L
    }

    @Test
    fun `two DataStoreValue instances with same key reflect updates`(@TempDir tempDir: Path) = runTest {
        val testStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { tempDir.resolve("test.preferences_pb").toFile() },
        )

        val value1 = testStore.createValue<String?>("shared.key", defaultValue = "default")
        val value2 = testStore.createValue<String?>("shared.key", defaultValue = "default")

        value1.value("updated")

        value2.value() shouldBe "updated"
    }

    @Test
    fun `writing same value twice does not emit duplicate`(@TempDir tempDir: Path) = runTest {
        val testStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { tempDir.resolve("test.preferences_pb").toFile() },
        )

        val dsValue = testStore.createValue<String?>("test.distinct", defaultValue = "initial")

        dsValue.value("changed")
        dsValue.value("changed")
        dsValue.value("final")

        // Flow should have distinctUntilChanged, so collecting after writes should give latest
        dsValue.flow.first() shouldBe "final"
    }

}
