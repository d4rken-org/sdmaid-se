package eu.darken.sdmse.squeezer.core.history

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class CompressionHistoryMigrationTest : BaseTest() {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CompressionHistoryRoomDb::class.java,
    )

    @Test
    fun `migration 1 to 2 adds outcome column with COMPRESSED default`() {
        helper.createDatabase("test-history.db", 1).use { db ->
            db.execSQL(
                "INSERT INTO compression_history (content_hash) VALUES ('hash-abc')"
            )
        }

        helper.runMigrationsAndValidate(
            "test-history.db", 2, true,
            CompressionHistoryDatabase.MIGRATION_1_2,
        ).use { db ->
            db.query("SELECT content_hash, outcome FROM compression_history WHERE content_hash = 'hash-abc'").use { cursor ->
                cursor.moveToFirst() shouldBe true
                cursor.getString(0) shouldBe "hash-abc"
                cursor.getString(1) shouldBe "COMPRESSED"
            }
        }
    }

    @Test
    fun `migration 1 to 2 preserves multiple entries`() {
        helper.createDatabase("test-history-multi.db", 1).use { db ->
            db.execSQL("INSERT INTO compression_history (content_hash) VALUES ('hash-1')")
            db.execSQL("INSERT INTO compression_history (content_hash) VALUES ('hash-2')")
            db.execSQL("INSERT INTO compression_history (content_hash) VALUES ('hash-3')")
        }

        helper.runMigrationsAndValidate(
            "test-history-multi.db", 2, true,
            CompressionHistoryDatabase.MIGRATION_1_2,
        ).use { db ->
            db.query("SELECT content_hash, outcome FROM compression_history ORDER BY content_hash").use { cursor ->
                cursor.count shouldBe 3

                cursor.moveToFirst() shouldBe true
                cursor.getString(0) shouldBe "hash-1"
                cursor.getString(1) shouldBe "COMPRESSED"

                cursor.moveToNext() shouldBe true
                cursor.getString(0) shouldBe "hash-2"
                cursor.getString(1) shouldBe "COMPRESSED"

                cursor.moveToNext() shouldBe true
                cursor.getString(0) shouldBe "hash-3"
                cursor.getString(1) shouldBe "COMPRESSED"
            }
        }
    }
}
