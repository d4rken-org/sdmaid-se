package eu.darken.sdmse.common.files.core.saf

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineExceptionHandler
import kotlinx.coroutines.test.createTestCoroutineScope
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import testhelper.EmptyApp
import testhelpers.coroutine.TestDispatcherProvider

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], application = EmptyApp::class)
class SAFGatewayTest {


    @Test
    fun `test storage root detection`() {
        val testScope =
            createTestCoroutineScope(TestCoroutineDispatcher() + TestCoroutineExceptionHandler() + EmptyCoroutineContext)
        val dispatcherProvider = TestDispatcherProvider()
        val safGateway = SAFGateway(
            mockk(),
            mockk(),
            testScope,
            dispatcherProvider
        )
        val nonRoot = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3Asafstor")
        safGateway.isStorageRoot(SAFPath.build(nonRoot)) shouldBe false
        safGateway.isStorageRoot(SAFPath.build(nonRoot, "crumb1")) shouldBe false

        val root = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A")
        safGateway.isStorageRoot(SAFPath.build(root)) shouldBe true
        safGateway.isStorageRoot(SAFPath.build(root, "crumb1")) shouldBe false
    }
}