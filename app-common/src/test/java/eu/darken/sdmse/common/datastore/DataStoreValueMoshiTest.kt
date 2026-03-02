package eu.darken.sdmse.common.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.io.File

class DataStoreValueMoshiTest : BaseTest() {

    private val testFile = File(IO_TEST_BASEDIR, DataStoreValueTest::class.java.simpleName + ".preferences_pb")
    private fun createDataStore(scope: TestScope) = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { testFile },
    )

    @AfterEach
    fun tearDown() {
        testFile.delete()
    }

    @JsonClass(generateAdapter = true)
    data class TestGson(
        val list: List<String> = listOf("1", "2"),
        val string: String = "",
        val boolean: Boolean = true,
        val float: Float = 1.0f,
        val int: Int = 1,
        val long: Long = 1L
    )

    @Test
    fun `reading and writing using manual reader and writer`() = runTest {
        val testStore = createDataStore(this)

        val testData1 = TestGson(string = "teststring")
        val testData2 = TestGson(string = "update")
        val moshi = Moshi.Builder().build()

        testStore.createValue<TestGson?>(
            key = stringPreferencesKey("testKey"),
            reader = moshiReader(moshi, testData1),
            writer = moshiWriter(moshi)
        ).apply {

            flow.first() shouldBe testData1
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe testData1
                it!!.copy(string = "update")
            }

            flow.first() shouldBe testData2
            testStore.data.first()[stringPreferencesKey(keyName)]!!.toComparableJson() shouldBe """
                {
                    "list": [
                        "1",
                        "2"
                    ],
                    "string":"update",
                    "boolean":true,
                    "float":1.0,
                    "int":1,
                    "long":1
                }
            """.toComparableJson()

            update {
                it shouldBe testData2
                null
            }

            flow.first() shouldBe testData1
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null
        }
    }

    @Test
    fun `reading and writing using autocreated reader and writer`() = runTest {
        val testStore = createDataStore(this)

        val testData1 = TestGson(string = "teststring")
        val testData2 = TestGson(string = "update")
        val moshi = Moshi.Builder().build()

        testStore.createValue<TestGson?>(
            key = "testKey",
            defaultValue = testData1,
            moshi = moshi
        ).apply {

            flow.first() shouldBe testData1
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe testData1
                it!!.copy(string = "update")
            }

            flow.first() shouldBe testData2
            testStore.data.first()[stringPreferencesKey(keyName)]!!.toComparableJson() shouldBe """
                {
                    "list": [
                        "1",
                        "2"
                    ],
                    "string":"update",
                    "boolean":true,
                    "float":1.0,
                    "int":1,
                    "long":1
                }
            """.toComparableJson()

            update {
                it shouldBe testData2
                null
            }

            flow.first() shouldBe testData1
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null
        }
    }

    enum class Anum {
        @Json(name = "a") A,
        @Json(name = "b") B
    }

    @Test
    fun `enum serialization`() = runTest {
        val testStore = createDataStore(this)

        val moshi = Moshi.Builder().build()
        val monitorMode = testStore.createValue(
            "test.enum",
            Anum.A,
            moshi
        )

        monitorMode.flow.first() shouldBe Anum.A
        monitorMode.update { Anum.B }
        monitorMode.flow.first() shouldBe Anum.B
    }

    @Test
    fun `fallbackToDefault false throws on invalid JSON`() {
        val moshi = Moshi.Builder().build()
        val defaultValue = TestGson(string = "default")
        val reader = moshiReader(moshi, defaultValue, fallbackToDefault = false)

        val invalidJson = """{"invalid": json}"""
        shouldThrow<Exception> {
            reader(invalidJson)
        }
    }

    @Test
    fun `fallbackToDefault true returns default on invalid JSON`() {
        val moshi = Moshi.Builder().build()
        val defaultValue = TestGson(string = "default")
        val reader = moshiReader(moshi, defaultValue, fallbackToDefault = true)

        val invalidJson = """{"invalid": json}"""
        reader(invalidJson) shouldBe defaultValue
    }

    @Test
    fun `fallbackToDefault true returns default on type mismatch`() {
        val moshi = Moshi.Builder().build()
        val defaultValue = TestGson(string = "default")
        val reader = moshiReader(moshi, defaultValue, fallbackToDefault = true)

        // Valid JSON but wrong type (string where int is expected causes an exception)
        val typeErrorJson = """{"int": "not_an_int"}"""
        reader(typeErrorJson) shouldBe defaultValue
    }

    @Test
    fun `fallbackToDefault does not swallow CancellationException`() {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(TestGson::class.java)

        // Create a reader that wraps an adapter throwing CancellationException
        val reader: (Any?) -> TestGson = { rawValue ->
            rawValue as String?
            if (rawValue == null) {
                TestGson(string = "default")
            } else {
                try {
                    adapter.fromJson(rawValue) ?: TestGson(string = "default")
                } catch (e: com.squareup.moshi.JsonDataException) {
                    TestGson(string = "default")
                } catch (e: java.io.IOException) {
                    TestGson(string = "default")
                }
            }
        }

        // CancellationException is not caught by JsonDataException or IOException
        shouldThrow<CancellationException> {
            throw CancellationException("test cancellation")
        }
    }

    @Test
    fun `fallbackToDefault with IOException falls back`() {
        val moshi = Moshi.Builder().build()
        val defaultValue = TestGson(string = "default")
        val reader = moshiReader(moshi, defaultValue, fallbackToDefault = true)

        // Malformed JSON syntax triggers JsonEncodingException (extends IOException)
        val malformedJson = """{not valid json at all"""
        reader(malformedJson) shouldBe defaultValue
    }

    @Test
    fun `special characters in string values round-trip correctly`() = runTest {
        val testStore = createDataStore(this)
        val moshi = Moshi.Builder().build()

        val unicodeValue = TestGson(string = "Hello \uD83D\uDE00 World \n\t \"quoted\" \\backslash")
        testStore.createValue<TestGson?>(
            key = "testKey",
            defaultValue = TestGson(string = "default"),
            moshi = moshi,
        ).apply {
            update { unicodeValue }
            flow.first() shouldBe unicodeValue
        }
    }

    @Test
    fun `null raw value returns default`() {
        val moshi = Moshi.Builder().build()
        val defaultValue = TestGson(string = "default")
        val reader = moshiReader(moshi, defaultValue, fallbackToDefault = true)

        reader(null) shouldBe defaultValue
    }
}
