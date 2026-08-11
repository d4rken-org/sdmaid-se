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
import eu.darken.sdmse.common.debug.exit.ExitInfoLogger
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.getPackageInfo
import eu.darken.sdmse.common.upgrade.UpgradeDiagnostics
import eu.darken.sdmse.main.core.CurriculumVitae
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant
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
    private val exitInfoLogger: ExitInfoLogger = mockk(relaxed = true)
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
        every { SystemClock.elapsedRealtime() } returns RECORDING_START
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
            exitInfoLogger = exitInfoLogger,
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
            state.recordingStartedAt shouldBe null
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
            state.recordingStartedAt shouldBe RECORDING_START
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
            state.recordingStartedAt shouldBe null
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
    inner class MinimumDuration {

        private suspend fun TestScope.startFreshRecording(): RecorderModule {
            File(externalDir, "force_debug_run").createNewFile()
            coEvery { recorderPath.value() } returns null

            val dispatcher = StandardTestDispatcher(testScheduler)
            val module = createModule(backgroundScope, dispatcher)
            advanceUntilIdle()

            module.state.first { it.isRecording }.recordingStartedAt shouldBe RECORDING_START
            return module
        }

        private suspend fun TestScope.startResumedRecording(): RecorderModule {
            val existingDir = File(externalDir, "debug/logs/existing_session").apply { mkdirs() }
            coEvery { recorderPath.value() } returns existingDir.path

            val dispatcher = StandardTestDispatcher(testScheduler)
            val module = createModule(backgroundScope, dispatcher)
            advanceUntilIdle()

            // No elapsedRealtime baseline survives a restart, so the fallback is used instead
            module.state.first { it.isRecording }.recordingStartedAt shouldBe null
            return module
        }

        /**
         * A leaked [Files] mock breaks JUnit's own @TempDir handling in every later test,
         * and there is no shared unmockkAll hook between tests here.
         */
        @AfterEach
        fun releaseFilesMock() {
            unmockkStatic(Files::class)
        }

        /** Makes the resumed-session fallback see a log dir created [age] ago. */
        private fun stubLogDirAge(age: Duration) {
            mockkStatic(Files::class)
            every { Files.readAttributes(any<Path>(), BasicFileAttributes::class.java) } returns
                    mockk<BasicFileAttributes>().apply {
                        every { creationTime() } returns FileTime.from(Instant.now().minus(age))
                    }
        }

        @Test
        fun `an 8 second recording is caught`() = runTest {
            val module = startFreshRecording()
            every { SystemClock.elapsedRealtime() } returns RECORDING_START + 8_000L

            module.requestStopRecorder() shouldBe RecorderModule.StopResult.TooShort
        }

        @Test
        fun `a recording just below the threshold is caught`() = runTest {
            val module = startFreshRecording()
            every { SystemClock.elapsedRealtime() } returns RECORDING_START + 9_999L

            module.requestStopRecorder() shouldBe RecorderModule.StopResult.TooShort
        }

        @Test
        fun `a recording at the threshold stops without prompting`() = runTest {
            val module = startFreshRecording()
            every { SystemClock.elapsedRealtime() } returns RECORDING_START + 10_000L

            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
        }

        @Test
        fun `being caught keeps recording, it must not half-stop the recorder`() = runTest {
            val module = startFreshRecording()
            every { SystemClock.elapsedRealtime() } returns RECORDING_START + 8_000L

            module.requestStopRecorder() shouldBe RecorderModule.StopResult.TooShort

            module.state.first().isRecording shouldBe true
            coVerify(exactly = 0) { mockRecorder.stop() }
        }

        @Test
        fun `a resumed session younger than the threshold is caught`() = runTest {
            val module = startResumedRecording()
            stubLogDirAge(Duration.ofSeconds(3))

            module.requestStopRecorder() shouldBe RecorderModule.StopResult.TooShort
        }

        @Test
        fun `a resumed session older than the threshold stops without prompting`() = runTest {
            val module = startResumedRecording()
            stubLogDirAge(Duration.ofMinutes(5))

            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
        }

        @Test
        fun `an unreadable log dir fails open rather than trapping the user`() = runTest {
            val module = startResumedRecording()
            mockkStatic(Files::class)
            every {
                Files.readAttributes(any<Path>(), BasicFileAttributes::class.java)
            } throws IOException("no attributes for you")

            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
        }

        @Test
        fun `a clock rollback across a resume does not flag the recording as too short`() = runTest {
            // The fallback measures a real log dir against the wall clock, and that clock can move
            // backwards between the recording starting and the user stopping it (NTP correction,
            // manual change). The resulting negative age must not be read as "just started" — that
            // would nag a user who has been recording for hours to keep recording.
            val module = startResumedRecording()
            module.wallClock = { Instant.now().minus(Duration.ofHours(1)) }

            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
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

    /**
     * A start that fails must not take the reactive loop with it: the loop is the only thing that
     * ever serves a start or stop request, so killing it wedges every later caller forever.
     */
    @Nested
    inner class StartFailures {

        private fun TestScope.moduleWith(startThrows: Boolean = false): RecorderModule {
            coEvery { recorderPath.value() } returns null
            if (startThrows) coEvery { mockRecorder.start(any()) } throws IOException("recorder broken")

            val module = createModule(backgroundScope, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()
            return module
        }

        @Test
        fun `a failed start surfaces to the caller instead of hanging`() = runTest {
            val module = moduleWith(startThrows = true)

            // Bounded: before the fix this call never returned at all.
            val error = withTimeout(START_ENVELOPE_MS) {
                shouldThrow<IOException> { module.startRecorder() }
            }

            error.message shouldBe "recorder broken"
            module.state.first().isRecording shouldBe false
        }

        @Test
        fun `a failed start leaves the loop alive for the next request`() = runTest {
            val module = moduleWith(startThrows = true)

            shouldThrow<IOException> { module.startRecorder() }

            coEvery { mockRecorder.start(any()) } returns Unit

            withTimeout(START_ENVELOPE_MS) { module.startRecorder() }

            module.state.first().isRecording shouldBe true
        }

        @Test
        fun `a failed start rolls back everything the attempt created`() = runTest {
            every { context.getPackageInfo() } throws IOException("no package info")
            val module = moduleWith()

            shouldThrow<IOException> { module.startRecorder() }

            // The recorder was already live: leaving it running orphans it where nothing can reach it.
            coVerify { mockRecorder.stop() }
            File(externalDir, "force_debug_run").exists() shouldBe false
            File(externalDir, "debug/logs").listFiles()?.toList() shouldBe emptyList()
            // A single slot only keeps the LAST matching call, and the order is the point here: the
            // attempt saves its new dir as the resume marker, the rollback has to clear it again.
            val pathUpdates = mutableListOf<(String?) -> String?>()
            coVerify(atLeast = 1) { recorderPath.update(capture(pathUpdates)) }
            val applied = pathUpdates.map { it("ignored") }
            applied.size shouldBe 2
            applied.first() shouldNotBe null
            applied.last() shouldBe null
        }

        @Test
        fun `a failed resume keeps the markers of the session it was resuming`() = runTest {
            val existingDir = File(externalDir, "debug/logs/existing_session").apply { mkdirs() }
            val triggerFile = File(externalDir, "force_debug_run").apply { createNewFile() }
            coEvery { recorderPath.value() } returns existingDir.path
            // Fail the resume at the recorder itself: it is the one step a resume definitely takes.
            // Its own mock, stubbed on the exact resume dir -- the shared mockRecorder carries a
            // permissive start(any()) stub from setup that would otherwise answer this call.
            val boom = IOException("recorder broken")
            val resumeRecorder: Recorder = mockk()
            coEvery { resumeRecorder.start(existingDir) } throws boom
            coEvery { resumeRecorder.stop() } returns Unit
            every { recorderProvider.get() } returns resumeRecorder

            val module = createModule(backgroundScope, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()

            // Bounded: a resume that never fails would otherwise hang here instead of failing.
            val state = withTimeout(START_ENVELOPE_MS) { module.state.first { it.startFailure != null } }
            state.startFailure shouldBe boom
            state.isRecording shouldBe false

            coVerify { resumeRecorder.stop() }
            // None of this was created by the failed attempt, so the session stays resumable.
            triggerFile.exists() shouldBe true
            existingDir.exists() shouldBe true
            coVerify(exactly = 0) { recorderPath.update(any()) }
        }

        @Test
        fun `a rollback that gets the start failure handed back to it still completes`() = runTest {
            // A recorder that rethrows the VERY instance its start() failed with when the rollback
            // stops it: addSuppressed rejects self-suppression with an IllegalArgumentException, and
            // that throw escaped the rollback before the failure was ever committed -- ending the
            // loop, which is the exact wedge this whole guard exists to prevent.
            File(externalDir, "force_debug_run").createNewFile()
            coEvery { recorderPath.value() } returns null
            val boom = IOException("recorder broken")
            val selfSuppressing: Recorder = mockk()
            coEvery { selfSuppressing.start(any()) } throws boom
            coEvery { selfSuppressing.stop() } throws boom
            every { recorderProvider.get() } returns selfSuppressing

            val module = createModule(backgroundScope, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()

            // Bounded: without the guard the failure is never committed and this waits forever.
            val state = withTimeout(START_ENVELOPE_MS) { module.state.first { it.startFailure != null } }
            state.startFailure shouldBe boom
            state.shouldRecord shouldBe false
            state.isRecording shouldBe false
            coVerify { selfSuppressing.stop() }

            // The loop survived the rollback and still serves the next request.
            every { recorderProvider.get() } returns mockRecorder

            withTimeout(START_ENVELOPE_MS) { module.startRecorder() }
            module.state.first().isRecording shouldBe true
        }

        @Test
        fun `a failing saved-path read fails the attempt, not the loop`() = runTest {
            // The read decides whether the attempt is a resume, so it belongs to the attempt: while
            // it sat outside the guard, a throwing DataStore ended the collector for good.
            val boom = IOException("datastore unreachable")
            var reads = 0
            coEvery { recorderPath.value() } coAnswers {
                reads++
                // 1st read is the state initializer, 2nd is the attempt this test fails.
                if (reads == 2) throw boom
                null
            }

            val module = createModule(backgroundScope, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()

            val error = withTimeout(START_ENVELOPE_MS) {
                shouldThrow<IOException> { module.startRecorder() }
            }
            error shouldBe boom
            module.state.first().shouldRecord shouldBe false

            withTimeout(START_ENVELOPE_MS) { module.startRecorder() }
            module.state.first().isRecording shouldBe true
        }

        @Test
        fun `a foreign cancellation reaches the caller as a start failure`() = runTest {
            // Not our scope being cancelled (ensureActive covers that one) but a cancellation from
            // inside the attempt, e.g. a timeout. Rethrown as-is it unwinds the caller as if THEY
            // had been cancelled, which the ViewModel error handler ignores: nothing is reported.
            coEvery { recorderPath.value() } returns null
            val foreign = CancellationException("header read timed out")
            coEvery { mockRecorder.start(any()) } throws foreign

            val module = createModule(backgroundScope, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()

            val error = withTimeout(START_ENVELOPE_MS) {
                shouldThrow<RecorderModule.StartFailedException> { module.startRecorder() }
            }
            error.cause shouldBe foreign
            // The state keeps the original untouched.
            module.state.first().startFailure shouldBe foreign

            coEvery { mockRecorder.start(any()) } returns Unit

            withTimeout(START_ENVELOPE_MS) { module.startRecorder() }
            module.state.first().isRecording shouldBe true
        }

        @Test
        fun `a stop request during an in-flight start waits for it`() = runTest {
            coEvery { recorderPath.value() } returns null
            val startGate = CompletableDeferred<Unit>()
            coEvery { mockRecorder.start(any()) } coAnswers { startGate.await() }
            // Second read is the stop request's duration check: long enough to actually stop.
            every { SystemClock.elapsedRealtime() } returnsMany listOf(
                RECORDING_START,
                RECORDING_START + 20_000L,
            )

            val module = createModule(backgroundScope, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()

            val starting = async { module.startRecorder() }
            advanceUntilIdle()

            val stopping = async { module.requestStopRecorder() }
            advanceUntilIdle()

            // Reading the state outside the lock answered "not recording" here, and the start
            // committed right afterwards - a recording the user had already asked to stop.
            stopping.isCompleted shouldBe false

            startGate.complete(Unit)

            withTimeout(START_ENVELOPE_MS) {
                stopping.await().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
                starting.await()
            }
            module.state.first().isRecording shouldBe false
        }

        @Test
        fun `concurrent start requests each get their own failure`() = runTest {
            val module = moduleWith(startThrows = true)

            withTimeout(START_ENVELOPE_MS) {
                val first = async { runCatching { module.startRecorder() } }
                val second = async { runCatching { module.startRecorder() } }

                // Serialized: neither request can clear the failure the other one is waiting for and
                // leave it hanging on a recording that is never coming.
                first.await().exceptionOrNull().shouldBeInstanceOf<IOException>()
                second.await().exceptionOrNull().shouldBeInstanceOf<IOException>()
            }
        }

        @Test
        fun `a throwing recorder stop still completes the stop request`() = runTest {
            val existingDir = File(externalDir, "debug/logs/existing_session").apply { mkdirs() }
            coEvery { recorderPath.value() } returns existingDir.path

            val module = createModule(backgroundScope, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()
            module.state.first { it.isRecording }

            coEvery { mockRecorder.stop() } throws IOException("cannot close")

            // A leaked recorder is logged, but the transition still commits - otherwise the state
            // keeps claiming to record and can never be stopped again.
            withTimeout(START_ENVELOPE_MS) { module.stopRecorder() } shouldBe existingDir
            module.state.first().isRecording shouldBe false
        }
    }

    companion object {
        /** Arbitrary fixed [SystemClock.elapsedRealtime] value that recordings start at. */
        private const val RECORDING_START = 12345L

        /** Virtual-time bound: a wedged request would otherwise only fail via the suite timeout. */
        private const val START_ENVELOPE_MS = 30_000L
    }
}
