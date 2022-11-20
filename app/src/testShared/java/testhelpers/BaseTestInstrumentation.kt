package testhelpers

import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import io.mockk.unmockkAll
import org.junit.AfterClass
import testhelpers.logging.JUnitLogger

abstract class BaseTestInstrumentation {

    init {
        Logging.clearAll()
        Logging.install(JUnitLogger())
        testClassName = this.javaClass.simpleName
    }

    companion object {
        private var testClassName: String? = null

        @JvmStatic
        @AfterClass
        fun onTestClassFinished() {
            unmockkAll()
            log(testClassName!!, VERBOSE) { "onTestClassFinished()" }
            Logging.clearAll()
        }
    }
}
