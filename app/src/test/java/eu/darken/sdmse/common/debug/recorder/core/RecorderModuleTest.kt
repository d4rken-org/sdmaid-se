package eu.darken.sdmse.common.debug.recorder.core

import android.content.Context
import android.content.res.Resources
import android.os.SystemClock
import androidx.core.content.pm.PackageInfoCompat
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.SDMId
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.DebugSettings
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.getPackageInfo
import eu.darken.sdmse.common.upgrade.UpgradeDiagnostics
import eu.darken.sdmse.main.core.CurriculumVitae
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Provider
import kotlin.system.measureTimeMillis

class RecorderModuleTest : BaseTest() {

    @TempDir lateinit var tempDir: File
    private lateinit var externalDir: File
    private lateinit var cacheDir: File

    private val context: Context = mockk(relaxed = true)
    private val debugSettings: DebugSettings = mockk()
    private val recorderPath: DataStoreValue<String?> = mockk()
    private val sdmId: SDMId = mockk()
    private val dataAreaManager: DataAreaManager = mockk()
    private val curriculumVitae: CurriculumVitae = mockk()
    private val upgradeDiagnostics: UpgradeDiagnostics = mockk()
    private val recorderProvider: Provider<Recorder> = mockk()
    private val mockRecorder: Recorder = mockk()

    private val dummyUpdated = DataStoreValue.Updated(old = null as String?, new = null as String?)

    @BeforeEach
    fun setup() {
        externalDir = File(tempDir, "external").apply { mkdirs() }
        cacheDir = File(tempDir, "cache").apply { mkdirs() }

        every { context.getExternalFilesDir(null) } returns externalDir
        every { context.cacheDir } returns cacheDir

        mockkStatic("eu.darken.sdmse.common.datastore.DataStoreValueKt")
        every { debugSettings.recorderPath } returns recorderPath
        coEvery { recorderPath.update(any()) } returns dummyUpdated

        every { sdmId.id } returns "abcd"
        every { dataAreaManager.latestState } returns emptyFlow()
        every { curriculumVitae.history } returns emptyFlow()

        coEvery { upgradeDiagnostics.debugInfo() } returns "BillingCache(test)"

        every { recorderProvider.get() } returns mockRecorder
        coEvery { mockRecorder.start(any()) } returns Unit
        coEvery { mockRecorder.stop() } returns Unit

        mockkObject(BuildConfigWrap)
        every { BuildConfigWrap.APPLICATION_ID } returns "eu.darken.sdmse.test"
        every { BuildConfigWrap.VERSION_CODE } returns 1L
        every { BuildConfigWrap.FLAVOR } returns BuildConfigWrap.Flavor.FOSS
        every { BuildConfigWrap.BUILD_TYPE } returns BuildConfigWrap.BuildType.DEV
        every { BuildConfigWrap.VERSION_NAME } returns "1.0.0-test"
        every { BuildConfigWrap.GIT_SHA } returns "abc123"
        every { BuildConfigWrap.DEBUG } returns true

        mockkObject(BuildWrap)
        every { BuildWrap.VERSION.SDK_INT } returns 33
        every { BuildWrap.FINGERPRINT } returns "test-fingerprint"

        mockkStatic(Resources::class)
        every { Resources.getSystem() } returns mockk(relaxed = true)

        mockkStatic(PackageInfoCompat::class)
        every { PackageInfoCompat.getLongVersionCode(any()) } returns 1L

        mockkStatic("eu.darken.sdmse.common.ContextExtensionsKt")
        every { context.getPackageInfo() } returns mockk(relaxed = true)

        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 12345L
    }

    private fun createModule(scope: kotlinx.coroutines.CoroutineScope, dispatcher: CoroutineDispatcher) =
        RecorderModule(
            context = context,
            appScope = scope,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            dataAreaManager = dataAreaManager,
            sdmId = sdmId,
            debugSettings = debugSettings,
            curriculumVitae = curriculumVitae,
            upgradeDiagnostics = upgradeDiagnostics,
            recorderProvider = recorderProvider,
        )

    @Nested
    inner class ResumeOnRestart {

        @Test
        fun `no trigger file and no saved path does not start recording`() = runTest {
            coEvery { recorderPath.value() } returns null

            val dispatcher = StandardTestDispatcher(testScheduler)
            val module = createModule(backgroundScope, dispatcher)
            advanceUntilIdle()

            val state = module.state.first()
            state.shouldRecord shouldBe false
            state.isRecording shouldBe false
            verify(exactly = 0) { recorderProvider.get() }
        }

        @Test
        fun `saved path exists on startup resumes with existing log dir`() = runTest {
            val existingDir = File(externalDir, "debug/logs/existing_session").apply { mkdirs() }
            coEvery { recorderPath.value() } returns existingDir.path

            val dispatcher = StandardTestDispatcher(testScheduler)
            val module = createModule(backgroundScope, dispatcher)
            advanceUntilIdle()

            val state = module.state.first { it.isRecording }
            state.currentLogDir shouldBe existingDir
            state.recordingStartedAt shouldBe 0L
            coVerify { mockRecorder.start(existingDir) }
        }

        @Test
        fun `trigger file exists without saved path starts new recording`() = runTest {
            File(externalDir, "force_debug_run").createNewFile()
            coEvery { recorderPath.value() } returns null

            val dispatcher = StandardTestDispatcher(testScheduler)
            val module = createModule(backgroundScope, dispatcher)
            advanceUntilIdle()

            val state = module.state.first { it.isRecording }
            state.recordingStartedAt shouldBe 12345L
            val pathSlot = slot<(String?) -> String?>()
            coVerify { recorderPath.update(capture(pathSlot)) }
            pathSlot.captured("ignored") shouldNotBe null
        }

        @Test
        fun `a failing upgrade diagnostics read does not prevent recording`() = runTest {
            File(externalDir, "force_debug_run").createNewFile()
            coEvery { recorderPath.value() } returns null
            coEvery { upgradeDiagnostics.debugInfo() } throws IOException("disk full")

            val dispatcher = StandardTestDispatcher(testScheduler)
            val module = createModule(backgroundScope, dispatcher)
            advanceUntilIdle()

            module.state.first { it.isRecording }.isRecording shouldBe true
        }

        @Test
        fun `a cancellation during the log header stops the uncommitted recorder`() = runTest {
            // The recorder is started before the header is written but only committed to the state
            // afterwards: a cancellation in between would orphan a running recorder that
            // stopRecorder() can never reach.
            File(externalDir, "force_debug_run").createNewFile()
            coEvery { recorderPath.value() } returns null
            coEvery { upgradeDiagnostics.debugInfo() } coAnswers { awaitCancellation() }

            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val moduleScope = CoroutineScope(dispatcher + Job())
            val module = createModule(moduleScope, dispatcher)
            // runCurrent(), not advanceUntilIdle(): advancing would jump virtual time past the
            // header's read deadline, and the cancellation has to arrive while the read is in flight.
            runCurrent()

            // Cancelling the module's scope cancels the in-flight header read.
            moduleScope.cancel()
            advanceUntilIdle()

            coVerify { mockRecorder.start(any()) }
            coVerify { mockRecorder.stop() }
            module.state.first().isRecording shouldBe false
        }

        @Test
        fun `a failing header read degrades to unavailable instead of aborting the recording`() = runTest {
            // Deliberate contract: debug recording is what a user reaches for when the app is
            // ALREADY misbehaving, so a broken header source must not be the thing that denies them
            // the log. Only an outer cancellation (above) still rolls the uncommitted recorder back.
            File(externalDir, "force_debug_run").createNewFile()
            coEvery { recorderPath.value() } returns null
            every { curriculumVitae.history } returns flow { throw IOException("disk full") }

            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val moduleScope = CoroutineScope(dispatcher + Job())
            val module = createModule(moduleScope, dispatcher)
            advanceUntilIdle()

            coVerify { mockRecorder.start(any()) }
            coVerify(exactly = 0) { mockRecorder.stop() }
            module.state.first().isRecording shouldBe true

            moduleScope.cancel()
        }

        @Test
        fun `upgrade diagnostics are not read when recording does not start`() = runTest {
            // Resolving diagnostics must stay inert and lazy: on GPlay this path must never be the
            // thing that first touches the billing stack.
            coEvery { recorderPath.value() } returns null

            val dispatcher = StandardTestDispatcher(testScheduler)
            createModule(backgroundScope, dispatcher)
            advanceUntilIdle()

            coVerify(exactly = 0) { upgradeDiagnostics.debugInfo() }
        }

        @Test
        fun `both trigger file and saved path resumes existing session`() = runTest {
            val existingDir = File(externalDir, "debug/logs/existing_session").apply { mkdirs() }
            File(externalDir, "force_debug_run").createNewFile()
            coEvery { recorderPath.value() } returns existingDir.path

            val dispatcher = StandardTestDispatcher(testScheduler)
            val module = createModule(backgroundScope, dispatcher)
            advanceUntilIdle()

            val state = module.state.first { it.isRecording }
            state.currentLogDir shouldBe existingDir
            state.recordingStartedAt shouldBe 0L
        }

        @Test
        fun `stopRecorder clears saved path and deletes trigger file`() = runTest {
            val existingDir = File(externalDir, "debug/logs/existing_session").apply { mkdirs() }
            val triggerFile = File(externalDir, "force_debug_run").apply { createNewFile() }
            coEvery { recorderPath.value() } returns existingDir.path

            val dispatcher = StandardTestDispatcher(testScheduler)
            val module = createModule(backgroundScope, dispatcher)
            advanceUntilIdle()

            module.state.first { it.isRecording }

            module.stopRecorder()
            advanceUntilIdle()

            val state = module.state.first { !it.isRecording }
            state.isRecording shouldBe false
            triggerFile.exists() shouldBe false
            val clearSlot = slot<(String?) -> String?>()
            coVerify(atLeast = 1) { recorderPath.update(capture(clearSlot)) }
            clearSlot.captured("ignored") shouldBe null
        }
    }

    @Nested
    inner class HeaderReads {

        private val logLines = CopyOnWriteArrayList<String>()
        private val logCapture = object : Logging.Logger {
            override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
                logLines.add(message)
            }
        }

        @BeforeEach
        fun installLogCapture() {
            Logging.install(logCapture)
        }

        @AfterEach
        fun removeLogCapture() {
            Logging.remove(logCapture)
        }

        /**
         * Real dispatchers on purpose: the header deadline is wall-clock, a virtual-time test would
         * skip it instead of exercising it. The recording is started via [RecorderModule.startRecorder]
         * so the seam can be set before any header read runs.
         */
        private suspend fun withRealtimeModule(
            headerTimeoutMs: Long = 300L,
            block: suspend (RecorderModule) -> Unit,
        ) {
            coEvery { recorderPath.value() } returns null
            val moduleScope = CoroutineScope(Dispatchers.IO + Job())
            try {
                val module = createModule(moduleScope, Dispatchers.IO)
                module.headerReadTimeoutMs = headerTimeoutMs
                block(module)
            } finally {
                moduleScope.cancel()
            }
        }

        @Test
        fun `a wedged data area read does not hold up the recording`() = runTest {
            every { dataAreaManager.latestState } returns flow { awaitCancellation() }

            withRealtimeModule { module ->
                module.startRecorder()

                module.state.first().isRecording shouldBe true
                logLines.any { it.startsWith("Data areas unavailable") } shouldBe true
                // The other sources are unaffected -- they were read concurrently.
                logLines.any { it.startsWith("Update history:") } shouldBe true
                logLines.any { it.startsWith("Upgrade diagnostics: ") } shouldBe true
            }
        }

        @Test
        fun `a wedged update history read does not hold up the recording`() = runTest {
            every { curriculumVitae.history } returns flow { awaitCancellation() }

            withRealtimeModule { module ->
                module.startRecorder()

                module.state.first().isRecording shouldBe true
                logLines.any { it.startsWith("Update history unavailable") } shouldBe true
                logLines.any { it.startsWith("Data areas: ") } shouldBe true
                logLines.any { it.startsWith("Upgrade diagnostics: ") } shouldBe true
            }
        }

        @Test
        fun `a wedged upgrade diagnostics read does not hold up the recording`() = runTest {
            coEvery { upgradeDiagnostics.debugInfo() } coAnswers { awaitCancellation() }

            withRealtimeModule { module ->
                module.startRecorder()

                module.state.first().isRecording shouldBe true
                logLines.any { it.startsWith("Upgrade diagnostics unavailable") } shouldBe true
                logLines.any { it.startsWith("Data areas: ") } shouldBe true
                logLines.any { it.startsWith("Update history:") } shouldBe true
            }
        }

        @Test
        fun `all header reads wedged at once still costs only one shared deadline`() = runTest {
            every { dataAreaManager.latestState } returns flow { awaitCancellation() }
            every { curriculumVitae.history } returns flow { awaitCancellation() }
            coEvery { upgradeDiagnostics.debugInfo() } coAnswers { awaitCancellation() }

            withRealtimeModule(headerTimeoutMs = 300L) { module ->
                val elapsed = measureTimeMillis { module.startRecorder() }

                // One budget for all three: they are read concurrently, so three wedged sources
                // cost the same as one. Bounding each source separately would cost three times as
                // much, and not bounding them at all would never return.
                elapsed shouldBeLessThan 750L
                module.state.first().isRecording shouldBe true
            }
        }

        @Test
        fun `a data area state that is not known yet is not reported as unavailable`() = runTest {
            // latestState legitimately starts out null -- that is a normal header line, not a failure.
            every { dataAreaManager.latestState } returns flowOf(null)

            withRealtimeModule { module ->
                module.startRecorder()

                logLines.any { it == "Data areas: (null)" } shouldBe true
                logLines.any { it.startsWith("Data areas unavailable") } shouldBe false
            }
        }

        @Test
        fun `a flavor without diagnostics is not reported as unavailable`() = runTest {
            // FOSS has nothing to report and returns null: no diagnostics line at all, and above all
            // not one claiming the read failed.
            coEvery { upgradeDiagnostics.debugInfo() } returns null

            withRealtimeModule { module ->
                module.startRecorder()

                module.state.first().isRecording shouldBe true
                logLines.any { it.startsWith("Upgrade diagnostics") } shouldBe false
            }
        }
    }
}
