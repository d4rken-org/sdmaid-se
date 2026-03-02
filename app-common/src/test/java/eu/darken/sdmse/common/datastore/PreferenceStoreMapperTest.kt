package eu.darken.sdmse.common.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class PreferenceStoreMapperTest : BaseTest() {

    private val testFile = File(IO_TEST_BASEDIR, PreferenceStoreMapperTest::class.java.simpleName + ".preferences_pb")
    private fun createDataStore(scope: TestScope) = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { testFile },
    )

    @AfterEach
    fun tearDown() {
        testFile.delete()
    }

    @Test
    fun `unknown key throws NotImplementedError`() = runTest {
        val testStore = createDataStore(this)
        val dsValue = testStore.createValue("known.key", defaultValue = "default")

        val mapper = PreferenceStoreMapper(dsValue)

        shouldThrow<NotImplementedError> {
            mapper.getString("unknown.key", null)
        }
    }

    @Test
    fun `getStringSet throws NotImplementedError`() = runTest {
        val testStore = createDataStore(this)
        val dsValue = testStore.createValue("known.key", defaultValue = "default")

        val mapper = PreferenceStoreMapper(dsValue)

        shouldThrow<NotImplementedError> {
            mapper.getStringSet("known.key", null)
        }
    }

    @Test
    fun `round-trip through mapper matches direct DataStoreValue access`() = runTest {
        val testStore = createDataStore(this)
        val dsValue = testStore.createValue("test.string", defaultValue = "initial")

        val mapper = PreferenceStoreMapper(dsValue)

        mapper.putString("test.string", "via-mapper")
        dsValue.flow.first() shouldBe "via-mapper"

        mapper.getString("test.string", null) shouldBe "via-mapper"
    }

    @Test
    fun `duplicate keys in constructor throws IllegalArgumentException`() = runTest {
        val testStore = createDataStore(this)
        val dsValue1 = testStore.createValue("duplicate.key", defaultValue = "a")
        val dsValue2 = testStore.createValue("duplicate.key", defaultValue = "b")

        shouldThrow<IllegalArgumentException> {
            PreferenceStoreMapper(dsValue1, dsValue2)
        }
    }
}
