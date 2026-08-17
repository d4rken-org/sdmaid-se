package eu.darken.sdmse.common.files

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.files.local.LocalPathLookupExtended
import eu.darken.sdmse.common.files.saf.SAFGateway
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.files.saf.SAFPathLookup
import eu.darken.sdmse.common.files.saf.SAFPathLookupExtended
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.storage.PathMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import okio.FileHandle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import testhelper.EmptyApp
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.io.IOException
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], application = EmptyApp::class)
class GatewaySwitchTest {

    private val safGateway = mockk<SAFGateway>(relaxed = true)
    private val localGateway = mockk<LocalGateway>(relaxed = true)
    private val mapper = mockk<PathMapper>()

    private val localPath = LocalPath.build("/data/data/com.some.app")
    private val safPath = SAFPath.build(
        "content://com.android.externalstorage.documents/tree/primary%3A".toUri(),
        "Android",
        "data",
    )

    private fun CoroutineScope.createSwitch(): GatewaySwitch {
        every { localGateway.sharedResource } returns SharedResource.createKeepAlive("local", this)
        every { safGateway.sharedResource } returns SharedResource.createKeepAlive("saf", this)
        return GatewaySwitch(
            appScope = this,
            dispatcherProvider = TestDispatcherProvider(),
            safGateway = safGateway,
            localGateway = localGateway,
            mapper = mapper,
        )
    }

    @Test fun `lookupExtended delegates to the matching gateway`() = runTest2(autoCancel = true) {
        val switch = createSwitch()

        val path = LocalPath.build("/data/data/com.some.app")
        val result = mockk<LocalPathLookupExtended>()
        coEvery { localGateway.lookupExtended(path) } returns result

        switch.lookupExtended(path) shouldBe result
    }

    @Test fun `lookupExtended AUTO falls back to the alternative gateway`() = runTest2(autoCancel = true) {
        val switch = createSwitch()

        val localPath = LocalPath.build("/data/data/com.some.app")
        val safPath = mockk<SAFPath> { every { pathType } returns APath.PathType.SAF }
        val safResult = mockk<SAFPathLookupExtended>()

        coEvery { localGateway.lookupExtended(localPath) } throws ReadException(path = localPath)
        coEvery { mapper.toSAFPath(localPath) } returns safPath
        coEvery { safGateway.lookupExtended(safPath) } returns safResult

        switch.lookupExtended(localPath, GatewaySwitch.Type.AUTO) shouldBe safResult
    }

    @Test fun `lookupExtended AUTO rethrows the original error when the fallback also fails`() =
        runTest2(autoCancel = true) {
            val switch = createSwitch()

            val localPath = LocalPath.build("/data/data/com.some.app")
            val safPath = mockk<SAFPath> { every { pathType } returns APath.PathType.SAF }
            val original = ReadException(message = "original", path = localPath)

            coEvery { localGateway.lookupExtended(localPath) } throws original
            coEvery { mapper.toSAFPath(localPath) } returns safPath
            coEvery { safGateway.lookupExtended(safPath) } throws ReadException(message = "alternative")

            val thrown = shouldThrow<ReadException> {
                switch.lookupExtended(localPath, GatewaySwitch.Type.AUTO)
            }
            thrown shouldBe original
        }

    @Test fun `getGateway returns the gateway matching the path type`() = runTest2(autoCancel = true) {
        val switch = createSwitch()

        switch.getGateway(APath.PathType.LOCAL) shouldBe localGateway
        switch.getGateway(APath.PathType.SAF) shouldBe safGateway
    }

    @Test fun `RAW paths have no gateway`() = runTest2(autoCancel = true) {
        val switch = createSwitch()
        val rawPath = RawPath.build("/some/raw/path")

        shouldThrow<NotImplementedError> { switch.exists(rawPath) }
        shouldThrow<NotImplementedError> { switch.canRead(rawPath) }
        shouldThrow<NotImplementedError> { switch.getGateway(APath.PathType.RAW) }
    }

    @Test fun `plain operations route local paths to the local gateway`() = runTest2(autoCancel = true) {
        val switch = createSwitch()
        val modifiedAt = Instant.ofEpochMilli(1234)
        val permissions = Permissions(0b111_101_101)
        val ownership = Ownership(1000L, 1001L)
        val fileHandle = mockk<FileHandle>()
        val lookup = mockk<LocalPathLookup>()
        val lookupExtended = mockk<LocalPathLookupExtended>()
        coEvery { localGateway.listFiles(localPath) } returns flowOf(localPath)
        coEvery { localGateway.du(localPath, any()) } returns 42L
        coEvery { localGateway.canRead(localPath) } returns true
        coEvery { localGateway.canWrite(localPath) } returns true
        coEvery { localGateway.file(localPath, true) } returns fileHandle
        coEvery { localGateway.createSymlink(localPath, localPath) } returns true
        coEvery { localGateway.setModifiedAt(localPath, modifiedAt) } returns true
        coEvery { localGateway.setPermissions(localPath, permissions) } returns true
        coEvery { localGateway.setOwnership(localPath, ownership) } returns true
        coEvery { localGateway.lookup(localPath) } returns lookup
        coEvery { localGateway.lookupFiles(localPath) } returns flowOf(lookup)
        coEvery { localGateway.lookupFilesExtended(localPath) } returns flowOf(lookupExtended)

        switch.createDir(localPath)
        switch.createFile(localPath)
        switch.listFiles(localPath)
        switch.walk(localPath, APathGateway.WalkOptions(pathDoesNotContain = setOf("skipme")))
        switch.du(localPath, APathGateway.DuOptions(abortOnError = true)) shouldBe 42L
        switch.canRead(localPath) shouldBe true
        switch.canWrite(localPath) shouldBe true
        switch.file(localPath, readWrite = true) shouldBe fileHandle
        switch.delete(localPath, recursive = true)
        switch.createSymlink(localPath, localPath) shouldBe true
        switch.setModifiedAt(localPath, modifiedAt) shouldBe true
        switch.setPermissions(localPath, permissions) shouldBe true
        switch.setOwnership(localPath, ownership) shouldBe true
        switch.lookup(localPath) shouldBe lookup
        switch.lookupFiles(localPath)
        switch.lookupFilesExtended(localPath)

        val walkOptions = slot<APathGateway.WalkOptions<LocalPath, LocalPathLookup>>()
        val duOptions = slot<APathGateway.DuOptions<LocalPath, LocalPathLookup>>()
        coVerify {
            localGateway.createDir(localPath)
            localGateway.createFile(localPath)
            localGateway.listFiles(localPath)
            localGateway.walk(localPath, capture(walkOptions))
            localGateway.du(localPath, capture(duOptions))
            localGateway.canRead(localPath)
            localGateway.canWrite(localPath)
            localGateway.file(localPath, true)
            localGateway.delete(localPath, true)
            localGateway.createSymlink(localPath, localPath)
            localGateway.setModifiedAt(localPath, modifiedAt)
            localGateway.setPermissions(localPath, permissions)
            localGateway.setOwnership(localPath, ownership)
            localGateway.lookup(localPath)
            localGateway.lookupFiles(localPath)
            localGateway.lookupFilesExtended(localPath)
        }
        walkOptions.captured.pathDoesNotContain shouldBe setOf("skipme")
        duOptions.captured.abortOnError shouldBe true
        verify { safGateway wasNot Called }
    }

    @Test fun `plain operations route SAF paths to the SAF gateway`() = runTest2(autoCancel = true) {
        val switch = createSwitch()
        val modifiedAt = Instant.ofEpochMilli(1234)
        val permissions = Permissions(0b111_101_101)
        val ownership = Ownership(1000L, 1001L)
        val fileHandle = mockk<FileHandle>()
        val lookup = mockk<SAFPathLookup>()
        val lookupExtended = mockk<SAFPathLookupExtended>()
        coEvery { safGateway.listFiles(safPath) } returns flowOf(safPath)
        coEvery { safGateway.du(safPath, any()) } returns 42L
        coEvery { safGateway.canRead(safPath) } returns true
        coEvery { safGateway.canWrite(safPath) } returns true
        coEvery { safGateway.file(safPath, true) } returns fileHandle
        coEvery { safGateway.createSymlink(safPath, safPath) } returns true
        coEvery { safGateway.setModifiedAt(safPath, modifiedAt) } returns true
        coEvery { safGateway.setPermissions(safPath, permissions) } returns true
        coEvery { safGateway.setOwnership(safPath, ownership) } returns true
        coEvery { safGateway.lookup(safPath) } returns lookup
        coEvery { safGateway.lookupFiles(safPath) } returns flowOf(lookup)
        coEvery { safGateway.lookupFilesExtended(safPath) } returns flowOf(lookupExtended)

        switch.createDir(safPath)
        switch.createFile(safPath)
        switch.listFiles(safPath)
        switch.walk(safPath, APathGateway.WalkOptions(pathDoesNotContain = setOf("skipme")))
        switch.du(safPath, APathGateway.DuOptions(abortOnError = true)) shouldBe 42L
        switch.canRead(safPath) shouldBe true
        switch.canWrite(safPath) shouldBe true
        switch.file(safPath, readWrite = true) shouldBe fileHandle
        switch.delete(safPath, recursive = true)
        switch.createSymlink(safPath, safPath) shouldBe true
        switch.setModifiedAt(safPath, modifiedAt) shouldBe true
        switch.setPermissions(safPath, permissions) shouldBe true
        switch.setOwnership(safPath, ownership) shouldBe true
        switch.lookup(safPath) shouldBe lookup
        switch.lookupFiles(safPath)
        switch.lookupFilesExtended(safPath)

        val walkOptions = slot<APathGateway.WalkOptions<SAFPath, SAFPathLookup>>()
        val duOptions = slot<APathGateway.DuOptions<SAFPath, SAFPathLookup>>()
        coVerify {
            safGateway.createDir(safPath)
            safGateway.createFile(safPath)
            safGateway.listFiles(safPath)
            safGateway.walk(safPath, capture(walkOptions))
            safGateway.du(safPath, capture(duOptions))
            safGateway.canRead(safPath)
            safGateway.canWrite(safPath)
            safGateway.file(safPath, true)
            safGateway.delete(safPath, true)
            safGateway.createSymlink(safPath, safPath)
            safGateway.setModifiedAt(safPath, modifiedAt)
            safGateway.setPermissions(safPath, permissions)
            safGateway.setOwnership(safPath, ownership)
            safGateway.lookup(safPath)
            safGateway.lookupFiles(safPath)
            safGateway.lookupFilesExtended(safPath)
        }
        walkOptions.captured.pathDoesNotContain shouldBe setOf("skipme")
        duOptions.captured.abortOnError shouldBe true
        verify { localGateway wasNot Called }
    }

    @Test fun `createSymlink keys on the link path`() = runTest2(autoCancel = true) {
        val switch = createSwitch()
        val otherLink = LocalPath.build("/data/data/com.some.app/link")
        coEvery { localGateway.createSymlink(otherLink, localPath) } returns true

        switch.createSymlink(otherLink, localPath) shouldBe true

        coVerify { localGateway.createSymlink(otherLink, localPath) }
    }

    @Test fun `createSymlink rejects mixed path types`() = runTest2(autoCancel = true) {
        val switch = createSwitch()

        shouldThrow<IllegalArgumentException> { switch.createSymlink(localPath, safPath) }
        shouldThrow<IllegalArgumentException> { switch.createSymlink(safPath, localPath) }

        verify { localGateway wasNot Called }
        verify { safGateway wasNot Called }
    }

    @Test fun `lookup CURRENT never falls back`() = runTest2(autoCancel = true) {
        val switch = createSwitch()
        coEvery { localGateway.lookup(localPath) } throws ReadException(message = "original", path = localPath)

        // mapper is a strict mock: any fallback attempt would blow up on an unstubbed call.
        shouldThrow<ReadException> { switch.lookup(localPath, GatewaySwitch.Type.CURRENT) }
        shouldThrow<ReadException> { switch.lookup(localPath) }
    }

    @Test fun `lookup AUTO maps to the alternative gateway`() = runTest2(autoCancel = true) {
        val switch = createSwitch()
        val safResult = mockk<SAFPathLookup>()
        coEvery { localGateway.lookup(localPath) } throws ReadException(path = localPath)
        coEvery { mapper.toSAFPath(localPath) } returns safPath
        coEvery { safGateway.lookup(safPath) } returns safResult

        switch.lookup(localPath, GatewaySwitch.Type.AUTO) shouldBe safResult
    }

    @Test fun `lookup AUTO rethrows the original error when the alternative fails too`() =
        runTest2(autoCancel = true) {
            val switch = createSwitch()
            val original = ReadException(message = "original", path = localPath)
            coEvery { localGateway.lookup(localPath) } throws original
            coEvery { mapper.toSAFPath(localPath) } returns safPath
            coEvery { safGateway.lookup(safPath) } throws ReadException(message = "alternative")

            val thrown = shouldThrow<ReadException> { switch.lookup(localPath, GatewaySwitch.Type.AUTO) }
            thrown shouldBeSameInstanceAs original
            thrown.suppressed.single().message shouldBe "alternative"
        }

    @Test fun `lookup AUTO rethrows the original error when there is no alternative path`() =
        runTest2(autoCancel = true) {
            val switch = createSwitch()
            val original = ReadException(message = "original", path = localPath)
            coEvery { localGateway.lookup(localPath) } throws original
            coEvery { mapper.toSAFPath(localPath) } returns null

            // An unmappable alternative must not mask why the primary access failed.
            val thrown = shouldThrow<ReadException> { switch.lookup(localPath, GatewaySwitch.Type.AUTO) }
            thrown shouldBeSameInstanceAs original
            thrown.suppressed.single().message shouldBe "Can't map to SAF <-> ${localPath.path}"
            coVerify(exactly = 0) { safGateway.lookup(any()) }
        }

    @Test fun `lookup AUTO falls back from SAF to the local gateway`() = runTest2(autoCancel = true) {
        val switch = createSwitch()
        val localResult = mockk<LocalPathLookup>()
        coEvery { safGateway.lookup(safPath) } throws ReadException(path = safPath)
        coEvery { mapper.toLocalPath(safPath) } returns localPath
        coEvery { localGateway.lookup(localPath) } returns localResult

        switch.lookup(safPath, GatewaySwitch.Type.AUTO) shouldBe localResult
    }

    @Test fun `lookup AUTO rethrows the original SAF error when there is no local path`() =
        runTest2(autoCancel = true) {
            val switch = createSwitch()
            val original = ReadException(message = "original", path = safPath)
            coEvery { safGateway.lookup(safPath) } throws original
            coEvery { mapper.toLocalPath(safPath) } returns null

            val thrown = shouldThrow<ReadException> { switch.lookup(safPath, GatewaySwitch.Type.AUTO) }
            thrown shouldBeSameInstanceAs original
            thrown.suppressed.single().message shouldBe "Can't map to LOCAL <-> ${safPath.path}"
            coVerify(exactly = 0) { localGateway.lookup(any()) }
        }

    @Test fun `lookupExtended AUTO rethrows the original error when there is no alternative path`() =
        runTest2(autoCancel = true) {
            val switch = createSwitch()
            val original = ReadException(message = "original", path = localPath)
            coEvery { localGateway.lookupExtended(localPath) } throws original
            coEvery { mapper.toSAFPath(localPath) } returns null

            shouldThrow<ReadException> {
                switch.lookupExtended(localPath, GatewaySwitch.Type.AUTO)
            } shouldBeSameInstanceAs original
            coVerify(exactly = 0) { safGateway.lookupExtended(any()) }
        }

    @Test fun `lookupFiles AUTO falls back and discards partial emissions`() = runTest2(autoCancel = true) {
        val switch = createSwitch()
        val partial = mockk<LocalPathLookup>()
        val safResult = mockk<SAFPathLookup>()
        coEvery { localGateway.lookupFiles(localPath) } returns flow {
            emit(partial)
            throw ReadException(message = "mid-collection", path = localPath)
        }
        coEvery { mapper.toSAFPath(localPath) } returns safPath
        coEvery { safGateway.lookupFiles(safPath) } returns flowOf(safResult)

        // The failure only happens while the flow is collected, the fallback still kicks in and
        // whatever the primary gateway already emitted is dropped.
        switch.lookupFiles(localPath, GatewaySwitch.Type.AUTO) shouldContainExactly listOf(safResult)
    }

    @Test fun `lookupFiles AUTO rethrows the original when the alternative fails mid-collection`() =
        runTest2(autoCancel = true) {
            val switch = createSwitch()
            val original = ReadException(message = "original", path = localPath)
            coEvery { localGateway.lookupFiles(localPath) } returns flow { throw original }
            coEvery { mapper.toSAFPath(localPath) } returns safPath
            coEvery { safGateway.lookupFiles(safPath) } returns flow {
                emit(mockk<SAFPathLookup>())
                throw ReadException(message = "alternative")
            }

            shouldThrow<ReadException> {
                switch.lookupFiles(localPath, GatewaySwitch.Type.AUTO)
            } shouldBe original
        }

    @Test fun `lookupFiles AUTO rethrows the original error when there is no alternative path`() =
        runTest2(autoCancel = true) {
            val switch = createSwitch()
            val original = ReadException(message = "original", path = localPath)
            coEvery { localGateway.lookupFiles(localPath) } returns flow { throw original }
            coEvery { mapper.toSAFPath(localPath) } returns null

            shouldThrow<ReadException> {
                switch.lookupFiles(localPath, GatewaySwitch.Type.AUTO)
            } shouldBeSameInstanceAs original
            coVerify(exactly = 0) { safGateway.lookupFiles(any()) }
        }

    @Test fun `lookupFilesExtended AUTO falls back on a collection-time failure`() = runTest2(autoCancel = true) {
        val switch = createSwitch()
        val safResult = mockk<SAFPathLookupExtended>()
        coEvery { localGateway.lookupFilesExtended(localPath) } returns flow {
            emit(mockk<LocalPathLookupExtended>())
            throw ReadException(message = "mid-collection", path = localPath)
        }
        coEvery { mapper.toSAFPath(localPath) } returns safPath
        coEvery { safGateway.lookupFilesExtended(safPath) } returns flowOf(safResult)

        switch.lookupFilesExtended(localPath, GatewaySwitch.Type.AUTO) shouldContainExactly listOf(safResult)
    }

    @Test fun `lookupFilesExtended AUTO rethrows the original when the alternative fails too`() =
        runTest2(autoCancel = true) {
            val switch = createSwitch()
            val original = ReadException(message = "original", path = localPath)
            coEvery { localGateway.lookupFilesExtended(localPath) } returns flow { throw original }
            coEvery { mapper.toSAFPath(localPath) } returns safPath
            coEvery { safGateway.lookupFilesExtended(safPath) } returns flow {
                throw ReadException(message = "alternative")
            }

            shouldThrow<ReadException> {
                switch.lookupFilesExtended(localPath, GatewaySwitch.Type.AUTO)
            } shouldBe original
        }

    @Test fun `lookupFilesExtended AUTO rethrows the original error when there is no alternative path`() =
        runTest2(autoCancel = true) {
            val switch = createSwitch()
            val original = ReadException(message = "original", path = localPath)
            coEvery { localGateway.lookupFilesExtended(localPath) } returns flow { throw original }
            coEvery { mapper.toSAFPath(localPath) } returns null

            shouldThrow<ReadException> {
                switch.lookupFilesExtended(localPath, GatewaySwitch.Type.AUTO)
            } shouldBeSameInstanceAs original
            coVerify(exactly = 0) { safGateway.lookupFilesExtended(any()) }
        }

    @Test fun `exists AUTO falls back to the alternative gateway`() = runTest2(autoCancel = true) {
        val switch = createSwitch()
        coEvery { localGateway.exists(localPath) } throws ReadException(path = localPath)
        coEvery { mapper.toSAFPath(localPath) } returns safPath
        coEvery { safGateway.exists(safPath) } returns true

        switch.exists(localPath, GatewaySwitch.Type.AUTO) shouldBe true
    }

    @Test fun `exists AUTO rethrows the original error when the alternative fails too`() =
        runTest2(autoCancel = true) {
            val switch = createSwitch()
            val original = ReadException(message = "original", path = localPath)
            coEvery { localGateway.exists(localPath) } throws original
            coEvery { mapper.toSAFPath(localPath) } returns safPath
            coEvery { safGateway.exists(safPath) } throws ReadException(message = "alternative")

            val thrown = shouldThrow<ReadException> { switch.exists(localPath, GatewaySwitch.Type.AUTO) }
            thrown shouldBeSameInstanceAs original
            thrown.suppressed.single().message shouldBe "alternative"
        }

    @Test fun `exists AUTO rethrows the original error when there is no alternative path`() =
        runTest2(autoCancel = true) {
            val switch = createSwitch()
            val original = ReadException(message = "original", path = localPath)
            coEvery { localGateway.exists(localPath) } throws original
            coEvery { mapper.toSAFPath(localPath) } returns null

            shouldThrow<ReadException> {
                switch.exists(localPath, GatewaySwitch.Type.AUTO)
            } shouldBeSameInstanceAs original
            coVerify(exactly = 0) { safGateway.exists(any()) }
        }

    @Test fun `FORCED_LOCAL maps a SAF path through the mapper`() = runTest2(autoCancel = true) {
        val switch = createSwitch()
        val result = mockk<LocalPathLookup>()
        coEvery { mapper.toLocalPath(safPath) } returns localPath
        coEvery { localGateway.lookup(localPath) } returns result

        switch.lookup(safPath, GatewaySwitch.Type.FORCED_LOCAL) shouldBe result
    }

    @Test fun `FORCED_SAF maps a local path through the mapper`() = runTest2(autoCancel = true) {
        val switch = createSwitch()
        val result = mockk<SAFPathLookup>()
        coEvery { mapper.toSAFPath(localPath) } returns safPath
        coEvery { safGateway.lookup(safPath) } returns result

        switch.lookup(localPath, GatewaySwitch.Type.FORCED_SAF) shouldBe result
    }

    @Test fun `a forced type that can't be mapped fails before any gateway is used`() =
        runTest2(autoCancel = true) {
            val switch = createSwitch()
            coEvery { mapper.toSAFPath(localPath) } returns null
            coEvery { mapper.toLocalPath(safPath) } returns null

            shouldThrow<IOException> {
                switch.lookup(localPath, GatewaySwitch.Type.FORCED_SAF)
            }.message shouldBe "Can't map $localPath to SAF"

            shouldThrow<IOException> {
                switch.lookup(safPath, GatewaySwitch.Type.FORCED_LOCAL)
            }.message shouldBe "Can't map $safPath to LOCAL"

            coVerify(exactly = 0) { localGateway.lookup(any()) }
            coVerify(exactly = 0) { safGateway.lookup(any()) }
        }
}
