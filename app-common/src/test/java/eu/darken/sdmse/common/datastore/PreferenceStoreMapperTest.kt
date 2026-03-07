package eu.darken.sdmse.common.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class PreferenceStoreMapperTest : BaseTest() {

    private val testFile = File(IO_TEST_BASEDIR, PreferenceStoreMapperTest::class.java.simpleName + ".preferences_pb")

    @AfterEach
    fun tearDown() {
        testFile.delete()
    }

    // PreferenceStoreMapper uses valueBlocking (runBlocking) internally because it implements
    // SharedPreferences which is a blocking API. Tests must use runBlocking, not runTest,
    // to avoid deadlocking the single-threaded test dispatcher.

    @Test
    fun `unknown key throws NotImplementedError`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val testStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { testFile },
        )
        val dsValue = testStore.createValue("known.key", defaultValue = "default")

        val mapper = PreferenceStoreMapper(dsValue)

        shouldThrow<NotImplementedError> {
            mapper.getString("unknown.key", null)
        }
        scope.cancel()
    }

    @Test
    fun `getStringSet throws NotImplementedError`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val testStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { testFile },
        )
        val dsValue = testStore.createValue("known.key", defaultValue = "default")

        val mapper = PreferenceStoreMapper(dsValue)

        shouldThrow<NotImplementedError> {
            mapper.getStringSet("known.key", null)
        }
        scope.cancel()
    }

    @Test
    fun `round-trip through mapper matches direct DataStoreValue access`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val testStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { testFile },
        )
        val dsValue = testStore.createValue("test.string", defaultValue = "initial")

        val mapper = PreferenceStoreMapper(dsValue)

        mapper.putString("test.string", "via-mapper")
        dsValue.flow.first() shouldBe "via-mapper"

        mapper.getString("test.string", null) shouldBe "via-mapper"
        scope.cancel()
    }

    @Test
    fun `duplicate keys in constructor throws IllegalArgumentException`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val testStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { testFile },
        )
        val dsValue1 = testStore.createValue("duplicate.key", defaultValue = "a")
        val dsValue2 = testStore.createValue("duplicate.key", defaultValue = "b")

        shouldThrow<IllegalArgumentException> {
            PreferenceStoreMapper(dsValue1, dsValue2)
        }
        scope.cancel()
    }
}
