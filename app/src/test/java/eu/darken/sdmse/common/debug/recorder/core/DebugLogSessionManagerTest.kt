package eu.darken.sdmse.common.debug.recorder.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import java.time.Instant

class DebugLogSessionManagerTest : BaseTest() {

    @TempDir lateinit var testDir: File
    private lateinit var logParent: File
    private lateinit var recorderModule: RecorderModule
    private lateinit var debugLogZipper: DebugLogZipper
    private lateinit var recorderState: MutableStateFlow<RecorderModule.State>

    @BeforeEach
    fun setup() {
        logParent = File(testDir, "logs").apply { mkdirs() }

        recorderState = MutableStateFlow(RecorderModule.State())
        recorderModule = mockk<RecorderModule>().apply {
            every { state } returns recorderState
            every { getLogDirectories() } returns listOf(logParent)
            coEvery { getCurrentLogDir() } returns null
        }
        debugLogZipper = mockk()
    }

    private fun createSessionDir(name: String, coreLogContent: String? = "log content"): File {
        val dir = File(logParent, name).apply { mkdirs() }
        if (coreLogContent != null) {
            File(dir, "core.log").writeText(coreLogContent)
        }
        return dir
    }

    private fun createZipFile(name: String, size: Int = 100): File {
        val zip = File(logParent, "$name.zip")
        if (size > 0) {
            zip.writeBytes(ByteArray(size))
        } else {
            zip.createNewFile()
        }
        return zip
    }

    /** Mock that actually creates the zip file, preventing infinite auto-zip loops */
    private fun mockZipperCreatesFile() {
        every { debugLogZipper.zip(any()) } answers {
            val logDir = firstArg<File>()
            val zipFile = File(logDir.parentFile, "${logDir.name}.zip")
            zipFile.writeBytes(ByteArray(50))
            zipFile
        }
    }

    /** Derive session ID consistent with how the manager does it */
    private fun sessionId(name: String): SessionId = SessionId("ext:$name")

    @Nested
    inner class ScanSessions {

        @Test
        fun `valid session with dir and non-empty zip is Finished`() = runTest {
            createSessionDir("session1")
            createZipFile("session1")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)
            val sessions = manager.sessions.first()

            sessions shouldHaveSize 1
            val session = sessions.first()
            session.shouldBeInstanceOf<DebugLogSession.Finished>()
            session.id shouldBe sessionId("session1")
            session.compressedSize shouldBe 100L
        }

        @Test
        fun `dir with 0-byte zip is Failed CORRUPT_ZIP`() = runTest {
            createSessionDir("session1")
            createZipFile("session1", size = 0)

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)
            val sessions = manager.sessions.first()

            sessions shouldHaveSize 1
            val session = sessions.first()
            session.shouldBeInstanceOf<DebugLogSession.Failed>()
            session.reason shouldBe DebugLogSession.Failed.Reason.CORRUPT_ZIP
        }

        @Test
        fun `orphan dir without core log is Failed MISSING_LOG`() = runTest {
            createSessionDir("session1", coreLogContent = null)

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)
            val sessions = manager.sessions.first()

            sessions shouldHaveSize 1
            val session = sessions.first()
            session.shouldBeInstanceOf<DebugLogSession.Failed>()
            session.reason shouldBe DebugLogSession.Failed.Reason.MISSING_LOG
        }

        @Test
        fun `orphan dir with empty core log is Failed EMPTY_LOG`() = runTest {
            createSessionDir("session1", coreLogContent = "")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)
            val sessions = manager.sessions.first()

            sessions shouldHaveSize 1
            val session = sessions.first()
            session.shouldBeInstanceOf<DebugLogSession.Failed>()
            session.reason shouldBe DebugLogSession.Failed.Reason.EMPTY_LOG
        }

        @Test
        fun `dir without core log but valid zip is Finished`() = runTest {
            createSessionDir("session1", coreLogContent = null)
            createZipFile("session1")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)
            val sessions = manager.sessions.first()

            sessions shouldHaveSize 1
            sessions.first().shouldBeInstanceOf<DebugLogSession.Finished>()
        }

        @Test
        fun `dir with empty core log but valid zip is Finished`() = runTest {
            createSessionDir("session1", coreLogContent = "")
            createZipFile("session1")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)
            val sessions = manager.sessions.first()

            sessions shouldHaveSize 1
            sessions.first().shouldBeInstanceOf<DebugLogSession.Finished>()
        }

        @Test
        fun `standalone valid zip without dir is Finished`() = runTest {
            createZipFile("session1")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)
            val sessions = manager.sessions.first()

            sessions shouldHaveSize 1
            val session = sessions.first()
            session.shouldBeInstanceOf<DebugLogSession.Finished>()
            session.compressedSize shouldBe 100L
        }

        @Test
        fun `standalone empty zip without dir is Failed CORRUPT_ZIP`() = runTest {
            createZipFile("session1", size = 0)

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)
            val sessions = manager.sessions.first()

            sessions shouldHaveSize 1
            val session = sessions.first()
            session.shouldBeInstanceOf<DebugLogSession.Failed>()
            session.reason shouldBe DebugLogSession.Failed.Reason.CORRUPT_ZIP
        }

        @Test
        fun `active recording dir is Recording`() = runTest {
            val dir = createSessionDir("session1")
            recorderState.value = RecorderModule.State(currentLogDir = dir)

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)
            val sessions = manager.sessions.first()

            sessions shouldHaveSize 1
            sessions.first().shouldBeInstanceOf<DebugLogSession.Recording>()
        }

        @Test
        fun `orphan dir with valid core log triggers auto-zip`() = runTest {
            createSessionDir("session1", coreLogContent = "some log data")
            mockZipperCreatesFile()

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)

            // Collect until auto-zip completes and session transitions to Finished
            val sessions = manager.sessions.first { sessions ->
                sessions.any { it is DebugLogSession.Finished }
            }
            sessions shouldHaveSize 1
            sessions.first().shouldBeInstanceOf<DebugLogSession.Finished>()
        }
    }

    @Nested
    inner class CompanionScanSessions {

        @Test
        fun `scanSessions classifies dirs correctly without overlay`() {
            createSessionDir("session1")
            createZipFile("session1")

            val sessions = DebugLogSessionManager.scanSessions(listOf(logParent), null)

            sessions shouldHaveSize 1
            sessions.first().shouldBeInstanceOf<DebugLogSession.Finished>()
            sessions.first().id shouldBe sessionId("session1")
        }

        @Test
        fun `scanSessions returns orphan with valid log as Zipping`() {
            createSessionDir("session1", coreLogContent = "data")

            val sessions = DebugLogSessionManager.scanSessions(listOf(logParent), null)

            sessions shouldHaveSize 1
            sessions.first().shouldBeInstanceOf<DebugLogSession.Zipping>()
        }

        @Test
        fun `scanSessions marks active recording correctly`() {
            val dir = createSessionDir("session1")

            val sessions = DebugLogSessionManager.scanSessions(listOf(logParent), dir)

            sessions shouldHaveSize 1
            sessions.first().shouldBeInstanceOf<DebugLogSession.Recording>()
        }
    }

    @Nested
    inner class SessionIdDerivation {

        @Test
        fun `directory derives ext prefix`() {
            val dir = File(logParent, "my_session").apply { mkdirs() }
            SessionId.derive(dir) shouldBe SessionId("ext:my_session")
        }

        @Test
        fun `zip file derives ext prefix without extension`() {
            val zip = File(logParent, "my_session.zip").apply { createNewFile() }
            SessionId.derive(zip) shouldBe SessionId("ext:my_session")
        }

        @Test
        fun `cache path derives cache prefix`() {
            val cacheParent = File(testDir, "cache/debug/logs").apply { mkdirs() }
            val dir = File(cacheParent, "my_session").apply { mkdirs() }
            SessionId.derive(dir) shouldBe SessionId("cache:my_session")
        }

        @Test
        fun `baseName strips prefix`() {
            SessionId("ext:my_session").baseName shouldBe "my_session"
            SessionId("cache:my_session").baseName shouldBe "my_session"
        }

        @Test
        fun `location returns prefix`() {
            SessionId("ext:my_session").location shouldBe "ext"
            SessionId("cache:my_session").location shouldBe "cache"
        }

        @Test
        fun `zip tmp file derives name including zip in basename`() {
            // nameWithoutExtension strips only the last extension: "session1.zip.tmp" → "session1.zip"
            // In practice scanSessions deletes these before they reach derive()
            val tmp = File(logParent, "session1.zip.tmp").apply { createNewFile() }
            SessionId.derive(tmp) shouldBe SessionId("ext:session1.zip")
        }

        @Test
        fun `non-zip non-directory file strips extension`() {
            val txt = File(logParent, "session1.txt").apply { createNewFile() }
            SessionId.derive(txt) shouldBe SessionId("ext:session1")
        }

        @Test
        fun `file in unknown path gets ext prefix`() {
            val unknownDir = File(testDir, "some/random/path").apply { mkdirs() }
            val dir = File(unknownDir, "my_session").apply { mkdirs() }
            SessionId.derive(dir) shouldBe SessionId("ext:my_session")
        }

        @Test
        fun `baseName handles colons in session name`() {
            // substringAfter(":") only splits on the first colon
            val id = SessionId("ext:my:session:name")
            id.location shouldBe "ext"
            id.baseName shouldBe "my:session:name"
        }

        @Test
        fun `same basename in ext and cache produces different ids`() {
            val extParent = File(testDir, "external/debug/logs").apply { mkdirs() }
            val cacheParent = File(testDir, "cache/debug/logs").apply { mkdirs() }

            val extDir = File(extParent, "session1").apply { mkdirs() }
            val cacheDir = File(cacheParent, "session1").apply { mkdirs() }

            val extId = SessionId.derive(extDir)
            val cacheId = SessionId.derive(cacheDir)

            extId shouldBe SessionId("ext:session1")
            cacheId shouldBe SessionId("cache:session1")
            extId shouldNotBe cacheId
        }

        @Test
        fun `derive roundtrip - baseName reconstructs original filename`() {
            val dir = File(logParent, "2025-03-09_143022").apply { mkdirs() }
            val id = SessionId.derive(dir)

            id.baseName shouldBe "2025-03-09_143022"
            // baseName can be used to find the file again
            File(logParent, id.baseName).exists() shouldBe true
        }
    }

    @Nested
    inner class FindOrphans {

        @Test
        fun `finds orphan Zipping sessions`() {
            createSessionDir("session1", coreLogContent = "data")
            val sessions = DebugLogSessionManager.scanSessions(listOf(logParent), null)

            val orphans = DebugLogSessionManager.findOrphans(sessions, emptySet(), emptySet())

            orphans shouldHaveSize 1
            orphans.first().first shouldBe sessionId("session1")
        }

        @Test
        fun `excludes sessions already being zipped`() {
            createSessionDir("session1", coreLogContent = "data")
            val sessions = DebugLogSessionManager.scanSessions(listOf(logParent), null)

            val orphans = DebugLogSessionManager.findOrphans(sessions, setOf(sessionId("session1")), emptySet())

            orphans shouldHaveSize 0
        }

        @Test
        fun `excludes sessions in pendingAutoZips`() {
            createSessionDir("session1", coreLogContent = "data")
            val sessions = DebugLogSessionManager.scanSessions(listOf(logParent), null)

            val orphans = DebugLogSessionManager.findOrphans(sessions, emptySet(), setOf(sessionId("session1")))

            orphans shouldHaveSize 0
        }

        @Test
        fun `does not find Finished or Failed sessions`() {
            createSessionDir("session1")
            createZipFile("session1")
            createSessionDir("session2", coreLogContent = null)

            val sessions = DebugLogSessionManager.scanSessions(listOf(logParent), null)

            val orphans = DebugLogSessionManager.findOrphans(sessions, emptySet(), emptySet())

            orphans shouldHaveSize 0
        }
    }

    @Nested
    inner class SortOrder {

        @Test
        fun `sessions are sorted deterministically`() {
            // Create with zips to avoid orphan auto-zip
            createSessionDir("zzz_session")
            createZipFile("zzz_session")
            createSessionDir("aaa_session")
            createZipFile("aaa_session")
            createSessionDir("mmm_session")
            createZipFile("mmm_session")

            val sessions = DebugLogSessionManager.scanSessions(listOf(logParent), null)

            sessions shouldHaveSize 3
            val ids = sessions.map { it.id.value }
            ids shouldBe ids.sortedWith(
                compareByDescending<String> { sessions.find { s -> s.id.value == it }!!.createdAt }.thenBy { it }
            )
        }

        @Test
        fun `sessions with same createdAt sorted by id ascending`() {
            createSessionDir("eu.darken.sdmse_1_20231114T120000Z_aaaa")
            createZipFile("eu.darken.sdmse_1_20231114T120000Z_aaaa")
            createSessionDir("eu.darken.sdmse_1_20231114T120000Z_zzzz")
            createZipFile("eu.darken.sdmse_1_20231114T120000Z_zzzz")

            val sessions = DebugLogSessionManager.scanSessions(listOf(logParent), null)

            sessions shouldHaveSize 2
            sessions[0].id.value shouldBe "ext:eu.darken.sdmse_1_20231114T120000Z_aaaa"
            sessions[1].id.value shouldBe "ext:eu.darken.sdmse_1_20231114T120000Z_zzzz"
        }
    }

    @Nested
    inner class ParseCreatedAt {

        @Test
        fun `returns creation time for existing file`() {
            val dir = File(logParent, "test_dir").apply { mkdirs() }
            val createdAt = DebugLogSessionManager.parseCreatedAt(dir)
            val diffMs = System.currentTimeMillis() - createdAt.toEpochMilli()
            (diffMs < 5000) shouldBe true
        }

        @Test
        fun `falls back to lastModified for non-existent file`() {
            val file = File(logParent, "nonexistent")
            val createdAt = DebugLogSessionManager.parseCreatedAt(file)
            createdAt.toEpochMilli() shouldBe 0L
        }

        @Test
        fun `parses new UTC format from directory name`() {
            val dir = File(logParent, "eu.darken.sdmse_1_20231114T120000Z_abcd").apply { mkdirs() }
            val createdAt = DebugLogSessionManager.parseCreatedAt(dir)
            createdAt shouldBe Instant.parse("2023-11-14T12:00:00Z")
        }

        @Test
        fun `parses old format from directory name`() {
            val dir = File(logParent, "eu.darken.sdmse_1_2023-11-14_12-00-00-000_abcd").apply { mkdirs() }
            val createdAt = DebugLogSessionManager.parseCreatedAt(dir)
            createdAt shouldBe Instant.parse("2023-11-14T12:00:00Z")
        }

        @Test
        fun `falls back to filesystem for unrecognized name`() {
            val dir = File(logParent, "random_name").apply { mkdirs() }
            val createdAt = DebugLogSessionManager.parseCreatedAt(dir)
            val diffMs = System.currentTimeMillis() - createdAt.toEpochMilli()
            (diffMs < 5000) shouldBe true
        }
    }

    @Nested
    inner class DiskSize {

        @Test
        fun `Finished session includes disk size from dir and zip`() {
            createSessionDir("session1", coreLogContent = "some content here")
            createZipFile("session1", size = 200)

            val sessions = DebugLogSessionManager.scanSessions(listOf(logParent), null)

            sessions shouldHaveSize 1
            val session = sessions.first()
            session.shouldBeInstanceOf<DebugLogSession.Finished>()
            (session.diskSize > 0) shouldBe true
            (session.diskSize >= 200) shouldBe true
        }

        @Test
        fun `Recording session has disk size`() {
            val dir = createSessionDir("session1", coreLogContent = "log data")

            val sessions = DebugLogSessionManager.scanSessions(listOf(logParent), dir)

            sessions shouldHaveSize 1
            val session = sessions.first()
            session.shouldBeInstanceOf<DebugLogSession.Recording>()
            (session.diskSize > 0) shouldBe true
        }

        @Test
        fun `standalone zip has disk size equal to zip length`() {
            createZipFile("session1", size = 500)

            val sessions = DebugLogSessionManager.scanSessions(listOf(logParent), null)

            sessions shouldHaveSize 1
            val session = sessions.first()
            session.shouldBeInstanceOf<DebugLogSession.Finished>()
            session.diskSize shouldBe 500L
        }
    }

    @Nested
    inner class Guards {

        @Test
        fun `delete throws when session id is in zippingIds`() = runTest {
            // Create a session with a finished zip so scan doesn't trigger auto-zip
            createSessionDir("session1")
            createZipFile("session1")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)
            val sessions = manager.sessions.first()
            sessions shouldHaveSize 1
            sessions.first().shouldBeInstanceOf<DebugLogSession.Finished>()

            // Create another session dir and set up a blocking zip mock
            val newDir = createSessionDir("session2")
            every { debugLogZipper.zip(newDir) } answers {
                // Never completes — simulates long-running zip
                Thread.sleep(Long.MAX_VALUE)
                error("unreachable")
            }
            coEvery { recorderModule.requestStopRecorder() } returns RecorderModule.StopResult.Stopped(
                sessionId = sessionId("session2"),
                logDir = newDir,
            )

            // Stop recording triggers zipSessionAsync → adds session2 to zippingIds
            manager.requestStopRecording()

            // Attempt delete while zipping — should throw
            shouldThrow<IllegalArgumentException> {
                manager.delete(sessionId("session2"))
            }

            // Session dir should still exist
            File(logParent, "session2").exists() shouldBe true
        }

        @Test
        fun `delete throws for active recording`() = runTest {
            val dir = createSessionDir("session1")
            recorderState.value = RecorderModule.State(currentLogDir = dir)
            coEvery { recorderModule.getCurrentLogDir() } returns dir

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)

            shouldThrow<IllegalArgumentException> {
                manager.delete(sessionId("session1"))
            }
        }
    }

    @Nested
    inner class ZipSession {

        @Test
        fun `zipSession returns existing valid zip`() = runTest {
            createSessionDir("session1")
            val zip = createZipFile("session1")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)

            val result = manager.zipSession(sessionId("session1"))
            result shouldBe zip
        }

        @Test
        fun `zipSession re-zips when dir is newer`() = runTest {
            val dir = createSessionDir("session1")
            val zip = createZipFile("session1")
            // Make the zip older than the dir
            zip.setLastModified(dir.lastModified() - 10_000)

            mockZipperCreatesFile()

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)

            val result = manager.zipSession(sessionId("session1"))
            result.exists() shouldBe true
            result.extension shouldBe "zip"
        }

        @Test
        fun `zipSession throws for active recording`() = runTest {
            val dir = createSessionDir("session1")
            recorderState.value = RecorderModule.State(currentLogDir = dir)
            coEvery { recorderModule.getCurrentLogDir() } returns dir

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)

            shouldThrow<IllegalArgumentException> {
                manager.zipSession(sessionId("session1"))
            }
        }

        @Test
        fun `zipSession throws when no directory found`() = runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)

            shouldThrow<IllegalArgumentException> {
                manager.zipSession(sessionId("nonexistent"))
            }
        }
    }

    @Nested
    inner class DualLocation {

        @Test
        fun `scanSessions from two directories produces distinct sessions`() {
            val extParent = File(testDir, "external/debug/logs").apply { mkdirs() }
            val cacheParent = File(testDir, "cache/debug/logs").apply { mkdirs() }

            // Same basename in both locations
            File(extParent, "session1").apply { mkdirs() }
            File(extParent, "session1.zip").apply { writeBytes(ByteArray(50)) }
            File(cacheParent, "session1").apply { mkdirs() }
            File(cacheParent, "session1.zip").apply { writeBytes(ByteArray(80)) }

            val sessions = DebugLogSessionManager.scanSessions(listOf(extParent, cacheParent), null)

            sessions shouldHaveSize 2
            val ids = sessions.map { it.id }.toSet()
            ids shouldBe setOf(SessionId("ext:session1"), SessionId("cache:session1"))
        }

        @Test
        fun `delete by baseName removes from all directories`() = runTest {
            // delete() iterates all logDirectories and removes by baseName,
            // so same-named sessions in different locations are both deleted.
            val extParent = File(testDir, "external/debug/logs").apply { mkdirs() }
            val cacheParent = File(testDir, "cache/debug/logs").apply { mkdirs() }

            File(extParent, "session1").apply { mkdirs() }
            File(extParent, "session1.zip").apply { writeBytes(ByteArray(50)) }
            File(cacheParent, "session1").apply { mkdirs() }
            File(cacheParent, "session1.zip").apply { writeBytes(ByteArray(80)) }

            every { recorderModule.getLogDirectories() } returns listOf(extParent, cacheParent)

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val manager = DebugLogSessionManager(backgroundScope, TestDispatcherProvider(testDispatcher), recorderModule, debugLogZipper)

            manager.delete(SessionId("ext:session1"))

            // Both locations are cleaned because delete() uses baseName across all directories
            File(extParent, "session1").exists() shouldBe false
            File(extParent, "session1.zip").exists() shouldBe false
            File(cacheParent, "session1").exists() shouldBe false
            File(cacheParent, "session1.zip").exists() shouldBe false
        }
    }

    @Nested
    inner class StaleTempCleanup {

        @Test
        fun `scanSessions cleans up stale zip tmp files`() {
            createSessionDir("session1")
            createZipFile("session1")
            val tmpFile = File(logParent, "session1.zip.tmp").apply { writeBytes(ByteArray(10)) }
            tmpFile.exists() shouldBe true

            DebugLogSessionManager.scanSessions(listOf(logParent), null)

            tmpFile.exists() shouldBe false
        }
    }
}
