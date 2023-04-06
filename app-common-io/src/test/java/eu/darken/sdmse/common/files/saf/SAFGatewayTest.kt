package eu.darken.sdmse.common.files.saf

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import testhelper.EmptyApp
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], application = EmptyApp::class)
class SAFGatewayTest {

    @Test
    fun `init`() = runTest2(autoCancel = true) {
        val dispatcherProvider = TestDispatcherProvider()
        val safGateway = SAFGateway(
            mockk(),
            mockk(),
            this,
            dispatcherProvider
        )
    }
}