package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseExpandablesFilterTestHelper
import eu.darken.sdmse.common.areas.DataArea.Type
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CodeCacheFilterTest : BaseExpandablesFilterTestHelper() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = CodeCacheFilter(
        environment = storageEnvironment,
    )

    @Test fun testDefaultFilter() = runTest {
        neg(testPkg, Type.PRIVATE_DATA, "com.tumblr", "code_cache")
        pos(testPkg, Type.PRIVATE_DATA, "com.tumblr", "code_cache", "test")

        confirm(create())
    }
}