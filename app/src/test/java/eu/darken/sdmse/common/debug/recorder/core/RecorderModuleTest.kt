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
import eu.darken.sdmse.common.getPackageInfo
import eu.darken.sdmse.main.core.CurriculumVitae
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import javax.inject.Provider

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
}
