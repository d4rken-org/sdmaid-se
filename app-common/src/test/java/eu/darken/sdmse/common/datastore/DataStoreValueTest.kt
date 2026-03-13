package eu.darken.sdmse.common.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
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
            valueBlocking shouldBe "default"
            value() shouldBe "default"
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe "default"
                "newvalue"
            } shouldBe DataStoreValue.Updated("default", "newvalue")

            valueBlocking shouldBe "newvalue"
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

}
