package testhelpers

import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterAll


open class BaseTest {
    init {
        Logging.clearAll()
        Logging.install(eu.darken.sdmse.common.JUnitLogger())
        testClassName = this.javaClass.simpleName
    }

    companion object {
        private var testClassName: String? = null
        const val IO_TEST_BASEDIR = "build/tmp/unit_tests"

        @JvmStatic
        @AfterAll
        fun onTestClassFinished() {
            unmockkAll()
            log(testClassName!!, VERBOSE) { "onTestClassFinished()" }
            Logging.clearAll()
        }
    }
}
