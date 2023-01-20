package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pos
import eu.darken.sdmse.common.areas.DataArea.Type
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefaultCachesPublicTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = DefaultCachesPublicFilter(
        environment = storageEnvironment,
    )

    @Test fun testDefaultFilter() = runTest {
        neg("com.tumblr", Type.PUBLIC_DATA, "com.tumblr", "files", "test")
        neg("com.tumblr", Type.PUBLIC_DATA, "com.tumblr", "cache", ".nomedia")
        pos(
            "com.tumblr",
            Type.PUBLIC_DATA,
            "com.tumblr",
            "cache",
            "image_manager_disk_cache",
            "f4efdb840bd81c4762c027e24537"
        )
        pos("com.tumblr", Type.PUBLIC_DATA, "com.tumblr", "cache", "image_manager_disk_cache")

        confirm(create())
    }
}