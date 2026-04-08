package eu.darken.sdmse.swiper.core.db

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
class SwiperDatabaseMigrationTest : BaseTest() {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SwiperRoomDb::class.java,
    )

    @Test
    fun `migration 1 to 2 adds file_type_filter column`() {
        // Create v1 database and insert a session
        helper.createDatabase("test-swiper.db", 1).use { db ->
            db.execSQL(
                """INSERT INTO swipe_sessions
                    (session_id, source_paths, current_index, total_items, created_at, last_modified_at, state, label, kept_count, deleted_count)
                    VALUES ('test-session', '[]', 0, 10, 1000, 2000, 'READY', 'My Session', 3, 2)"""
            )
        }

        // Run migration and validate
        helper.runMigrationsAndValidate("test-swiper.db", 2, true, SwiperDatabaseModule.MIGRATION_1_2).use { db ->
            db.query("SELECT file_type_filter FROM swipe_sessions WHERE session_id = 'test-session'").use { cursor ->
                cursor.moveToFirst() shouldBe true
                cursor.isNull(0) shouldBe true // New column defaults to NULL
            }

            // Verify existing data is preserved
            db.query("SELECT session_id, label, total_items, kept_count, deleted_count, state FROM swipe_sessions").use { cursor ->
                cursor.moveToFirst() shouldBe true
                cursor.getString(0) shouldBe "test-session"
                cursor.getString(1) shouldBe "My Session"
                cursor.getInt(2) shouldBe 10
                cursor.getInt(3) shouldBe 3
                cursor.getInt(4) shouldBe 2
                cursor.getString(5) shouldBe "READY"
            }
        }
    }

    @Test
    fun `migration 1 to 2 preserves multiple sessions`() {
        helper.createDatabase("test-swiper-multi.db", 1).use { db ->
            db.execSQL(
                """INSERT INTO swipe_sessions
                    (session_id, source_paths, current_index, total_items, created_at, last_modified_at, state, kept_count, deleted_count)
                    VALUES ('session-1', '[]', 0, 5, 1000, 2000, 'CREATED', 0, 0)"""
            )
            db.execSQL(
                """INSERT INTO swipe_sessions
                    (session_id, source_paths, current_index, total_items, created_at, last_modified_at, state, kept_count, deleted_count)
                    VALUES ('session-2', '[]', 3, 15, 3000, 4000, 'READY', 5, 3)"""
            )
        }

        helper.runMigrationsAndValidate("test-swiper-multi.db", 2, true, SwiperDatabaseModule.MIGRATION_1_2).use { db ->
            db.query("SELECT session_id, file_type_filter FROM swipe_sessions ORDER BY session_id").use { cursor ->
                cursor.count shouldBe 2

                cursor.moveToFirst() shouldBe true
                cursor.getString(0) shouldBe "session-1"
                cursor.isNull(1) shouldBe true

                cursor.moveToNext() shouldBe true
                cursor.getString(0) shouldBe "session-2"
                cursor.isNull(1) shouldBe true
            }
        }
    }

    @Test
    fun `migration 2 to 3 adds sort_order column with default OLDEST_FIRST`() {
        helper.createDatabase("test-swiper-23.db", 2).use { db ->
            db.execSQL(
                """INSERT INTO swipe_sessions
                    (session_id, source_paths, current_index, total_items, created_at, last_modified_at, state, label, kept_count, deleted_count, file_type_filter)
                    VALUES ('test', '[]', 0, 10, 1000, 2000, 'READY', 'X', 3, 2, NULL)"""
            )
        }
        helper.runMigrationsAndValidate("test-swiper-23.db", 3, true, SwiperDatabaseModule.MIGRATION_2_3).use { db ->
            db.query("SELECT sort_order FROM swipe_sessions WHERE session_id = 'test'").use { c ->
                c.moveToFirst() shouldBe true
                c.getString(0) shouldBe "OLDEST_FIRST"
            }
        }
    }

    @Test
    fun `migration 1 to 3 chains both migrations`() {
        helper.createDatabase("test-swiper-13.db", 1).use { db ->
            db.execSQL(
                """INSERT INTO swipe_sessions
                    (session_id, source_paths, current_index, total_items, created_at, last_modified_at, state, kept_count, deleted_count)
                    VALUES ('test', '[]', 0, 5, 1000, 2000, 'CREATED', 0, 0)"""
            )
        }
        helper.runMigrationsAndValidate(
            "test-swiper-13.db", 3, true,
            SwiperDatabaseModule.MIGRATION_1_2,
            SwiperDatabaseModule.MIGRATION_2_3,
        ).use { db ->
            db.query("SELECT file_type_filter, sort_order FROM swipe_sessions WHERE session_id = 'test'").use { c ->
                c.moveToFirst() shouldBe true
                c.isNull(0) shouldBe true
                c.getString(1) shouldBe "OLDEST_FIRST"
            }
        }
    }
}
