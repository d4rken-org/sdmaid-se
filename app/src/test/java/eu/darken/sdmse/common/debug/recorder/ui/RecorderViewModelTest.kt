package eu.darken.sdmse.common.debug.recorder.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSession
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSessionManager
import eu.darken.sdmse.common.debug.recorder.core.DebugLogZipper
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class RecorderViewModelTest : BaseTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val zipName = "eu.darken.sdmse_20004000_20261006T135328Z_efb3.zip"
    private val uri = Uri.parse("content://eu.darken.sdmse.provider/debug/$zipName")

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `share intent uses the file name as subject`() {
        val intent = RecorderViewModel.createShareIntent(uri, zipName)

        intent.getStringExtra(Intent.EXTRA_SUBJECT) shouldBe zipName
        intent.action shouldBe Intent.ACTION_SEND
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) shouldBe uri
        intent.clipData.shouldNotBeNull().apply {
            itemCount shouldBe 1
            getItemAt(0).uri shouldBe uri
        }
        intent.type shouldBe "application/zip"
        intent.hasCategory(Intent.CATEGORY_DEFAULT) shouldBe true
        intent.hasExtra(Intent.EXTRA_TEXT) shouldBe true
        intent.getStringExtra(Intent.EXTRA_TEXT) shouldBe ""
        (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) shouldBe Intent.FLAG_GRANT_READ_URI_PERMISSION
        (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) shouldBe Intent.FLAG_ACTIVITY_NEW_TASK
    }

    @Test
    fun `share emits a chooser for the zipped session named after the zip file`() = runTest2 {
        val sessionId = SessionId("cache:${zipName.removeSuffix(".zip")}")
        val zipFile = File("/data/user/0/eu.darken.sdmse/cache/debug/logs/$zipName")
        val session = DebugLogSession.Finished(
            id = sessionId,
            createdAt = Instant.EPOCH,
            logDir = File(zipFile.parentFile, sessionId.baseName),
            diskSize = 1L,
            zipFile = zipFile,
            compressedSize = 1L,
        )
        val sessionManager = mockk<DebugLogSessionManager>().apply {
            every { sessions } returns flowOf(listOf(session))
            coEvery { zipSession(sessionId) } returns zipFile
        }
        val debugLogZipper = mockk<DebugLogZipper>().apply {
            every { getUriForZip(zipFile) } returns uri
        }

        val vm = RecorderViewModel(
            handle = SavedStateHandle(mapOf(RecorderActivity.EXTRA_SESSION_ID to sessionId.value)),
            dispatcherProvider = TestDispatcherProvider(),
            context = context,
            webpageTool = mockk(relaxed = true),
            sessionManager = sessionManager,
            debugLogZipper = debugLogZipper,
            generalSettings = mockk(relaxed = true),
        )

        vm.share()

        val chooser = vm.events.first().shouldBeInstanceOf<RecorderViewModel.Event.LaunchShare>().intent
        chooser.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString() shouldBe
            context.getString(R.string.debug_debuglog_file_label)
        val shared = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java).shouldNotBeNull()
        shared.getStringExtra(Intent.EXTRA_SUBJECT) shouldBe zipFile.name
        shared.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) shouldBe uri
    }
}
