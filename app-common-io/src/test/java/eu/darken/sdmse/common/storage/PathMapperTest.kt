package eu.darken.sdmse.common.storage

import android.content.ContentResolver
import android.content.Intent
import android.content.UriPermission
import android.net.Uri
import androidx.core.net.toUri
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.runTest2
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class PathMapperTest : BaseTest() {

    private val contentResolver = mockk<ContentResolver>()
    private val storageManager2 = mockk<StorageManager2>()

    private val primaryTreeUri = "content://com.android.externalstorage.documents/tree/primary%3A".toUri()
    private val sdcardTreeUri = "content://com.android.externalstorage.documents/tree/1234-5678%3A".toUri()

    private fun volume(directory: String?, treeUri: Uri) = mockk<StorageVolumeX> {
        every { this@mockk.directory } returns directory?.let { File(it) }
        every { this@mockk.treeUri } returns treeUri
    }

    private fun mapper(vararg volumes: StorageVolumeX): PathMapper {
        every { storageManager2.storageVolumes } returns volumes.toList()
        return PathMapper(contentResolver, storageManager2)
    }

    private fun permission(uri: Uri, read: Boolean = true, write: Boolean = true) = mockk<UriPermission> {
        every { this@mockk.uri } returns uri
        every { isReadPermission } returns read
        every { isWritePermission } returns write
    }

    @Test fun `a path inside a volume maps to a SAF path with the prefix stripped`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        mapper.toSAFPath(LocalPath.build("/storage/emulated/0/Android/data/some.pkg")) shouldBe
            SAFPath.build(primaryTreeUri, "Android", "data", "some.pkg")
    }

    @Test fun `a path equal to the volume root maps to the bare tree uri`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        mapper.toSAFPath(LocalPath.build("/storage/emulated/0")) shouldBe SAFPath.build(primaryTreeUri)
    }

    @Test fun `the matching volume decides the tree uri`() = runTest2 {
        val mapper = mapper(
            volume("/storage/emulated/0", primaryTreeUri),
            volume("/storage/1234-5678", sdcardTreeUri),
        )

        mapper.toSAFPath(LocalPath.build("/storage/1234-5678/Music")) shouldBe
            SAFPath.build(sdcardTreeUri, "Music")
    }

    @Test fun `volumes without a directory are skipped`() = runTest2 {
        val mapper = mapper(
            volume(null, sdcardTreeUri),
            volume("/storage/emulated/0", primaryTreeUri),
        )

        mapper.toSAFPath(LocalPath.build("/storage/emulated/0/Music")) shouldBe
            SAFPath.build(primaryTreeUri, "Music")
    }

    @Test fun `a path outside every volume does not map`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        mapper.toSAFPath(LocalPath.build("/data/data/some.pkg")) shouldBe null
    }

    @Test fun `a failing volume listing does not map`() = runTest2 {
        every { storageManager2.storageVolumes } throws IllegalStateException("no volumes for you")
        val mapper = PathMapper(contentResolver, storageManager2)

        mapper.toSAFPath(LocalPath.build("/storage/emulated/0/Music")) shouldBe null
    }

    @Test fun `a sibling volume name is not matched as a prefix`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        mapper.toSAFPath(LocalPath.build("/storage/emulated/01/file")) shouldBe null
    }

    @Test fun `a sibling user volume is not matched as a prefix`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/1", primaryTreeUri))

        mapper.toSAFPath(LocalPath.build("/storage/emulated/10/x")) shouldBe null
    }

    @Test fun `the volume prefix is only stripped from the front`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        mapper.toSAFPath(LocalPath.build("/storage/emulated/0/backup/storage/emulated/0/f")) shouldBe
            SAFPath.build(primaryTreeUri, "backup", "storage", "emulated", "0", "f")
    }

    @Test fun `a path with traversal segments does not map`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        mapper.toSAFPath(LocalPath.build("/storage/emulated/0/Android/../../../etc/hosts")) shouldBe null
    }

    @Test fun `a path with a compound traversal segment does not map`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        mapper.toSAFPath(LocalPath.build("/storage/emulated/0", "safe/../../etc")) shouldBe null
    }

    @Test fun `a path with a current directory segment does not map`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        mapper.toSAFPath(LocalPath.build("/storage/emulated/0/./Music")) shouldBe null
    }

    @Test fun `names that only look like traversal still map`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        mapper.toSAFPath(LocalPath.build("/storage/emulated/0/foo..bar/.../.nomedia")) shouldBe
            SAFPath.build(primaryTreeUri, "foo..bar", "...", ".nomedia")
    }

    @Test fun `overlapping volume roots are resolved by specificity`() = runTest2 {
        val mapper = mapper(
            volume("/storage/emulated", sdcardTreeUri),
            volume("/storage/emulated/0", primaryTreeUri),
        )

        mapper.toSAFPath(LocalPath.build("/storage/emulated/0/Music")) shouldBe
            SAFPath.build(primaryTreeUri, "Music")
    }

    @Test fun `a SAF path maps back to the volume directory plus its segments`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        mapper.toLocalPath(SAFPath.build(primaryTreeUri, "Android", "data")) shouldBe
            LocalPath.build("/storage/emulated/0/Android/data")
    }

    @Test fun `a SAF path without segments maps to the volume directory`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        mapper.toLocalPath(SAFPath.build(primaryTreeUri)) shouldBe LocalPath.build("/storage/emulated/0")
    }

    @Test fun `an unknown tree uri does not map`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        mapper.toLocalPath(SAFPath.build(sdcardTreeUri, "Music")) shouldBe null
    }

    @Test fun `a failing volume listing does not map back`() = runTest2 {
        every { storageManager2.storageVolumes } throws IllegalStateException("no volumes for you")
        val mapper = PathMapper(contentResolver, storageManager2)

        mapper.toLocalPath(SAFPath.build(primaryTreeUri, "Music")) shouldBe null
    }

    @Test fun `a SAF path with traversal segments does not map back`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        mapper.toLocalPath(SAFPath.build(primaryTreeUri, "..", "..", "etc", "hosts")) shouldBe null
    }

    @Test fun `a SAF path with a compound traversal segment does not map back`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        mapper.toLocalPath(SAFPath.build(primaryTreeUri, "safe/../../etc")) shouldBe null
        mapper.toLocalPath(SAFPath.build(primaryTreeUri, "Android", "../etc")) shouldBe null
    }

    @Test fun `a SAF path with a current directory segment does not map back`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        mapper.toLocalPath(SAFPath.build(primaryTreeUri, ".", "Music")) shouldBe null
    }

    @Test fun `SAF names that only look like traversal still map back`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        mapper.toLocalPath(SAFPath.build(primaryTreeUri, "foo..bar", "...", ".nomedia")) shouldBe
            LocalPath.build("/storage/emulated/0/foo..bar/.../.nomedia")
    }

    @Test fun `a local path round-trips through the SAF mapping`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        val nested = LocalPath.build("/storage/emulated/0/Android/data/some.pkg")
        mapper.toLocalPath(mapper.toSAFPath(nested)!!) shouldBe nested

        val volumeRoot = LocalPath.build("/storage/emulated/0")
        mapper.toLocalPath(mapper.toSAFPath(volumeRoot)!!) shouldBe volumeRoot
    }

    @Test fun `a SAF path round-trips through the local mapping`() = runTest2 {
        val mapper = mapper(volume("/storage/emulated/0", primaryTreeUri))

        val nested = SAFPath.build(primaryTreeUri, "Android", "data", "some.pkg")
        mapper.toSAFPath(mapper.toLocalPath(nested)!!) shouldBe nested

        val volumeRoot = SAFPath.build(primaryTreeUri)
        mapper.toSAFPath(mapper.toLocalPath(volumeRoot)!!) shouldBe volumeRoot
    }

    @Test fun `takePermission persists a read-write grant`() {
        every { contentResolver.persistedUriPermissions } returns emptyList()
        every { contentResolver.takePersistableUriPermission(any(), any()) } just runs
        val mapper = PathMapper(contentResolver, storageManager2)

        mapper.takePermission(primaryTreeUri)

        verify {
            contentResolver.takePersistableUriPermission(
                primaryTreeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    @Test fun `takePermission is skipped when the uri is already persisted`() {
        every { contentResolver.persistedUriPermissions } returns listOf(permission(primaryTreeUri))
        val mapper = PathMapper(contentResolver, storageManager2)

        mapper.takePermission(primaryTreeUri)

        verify(exactly = 0) { contentResolver.takePersistableUriPermission(any(), any()) }
    }

    @Test fun `a failed take releases the grant and rethrows`() {
        every { contentResolver.persistedUriPermissions } returns emptyList()
        every { contentResolver.takePersistableUriPermission(any(), any()) } throws SecurityException("nope")
        every { contentResolver.releasePersistableUriPermission(any(), any()) } just runs
        val mapper = PathMapper(contentResolver, storageManager2)

        shouldThrow<SecurityException> { mapper.takePermission(primaryTreeUri) }

        verifyOrder {
            contentResolver.takePersistableUriPermission(primaryTreeUri, any())
            contentResolver.releasePersistableUriPermission(primaryTreeUri, any())
        }
    }

    @Test fun `a failing release during error handling is swallowed`() {
        every { contentResolver.persistedUriPermissions } returns emptyList()
        every { contentResolver.takePersistableUriPermission(any(), any()) } throws SecurityException("nope")
        every {
            contentResolver.releasePersistableUriPermission(any(), any())
        } throws SecurityException("nope either")
        val mapper = PathMapper(contentResolver, storageManager2)

        // The original failure is what callers get to see, not the cleanup failure.
        shouldThrow<SecurityException> { mapper.takePermission(primaryTreeUri) }.message shouldBe "nope"
    }

    @Test fun `releasePermission releases the tree root uri`() {
        every { contentResolver.persistedUriPermissions } returns emptyList()
        every { contentResolver.releasePersistableUriPermission(any(), any()) } just runs
        val mapper = PathMapper(contentResolver, storageManager2)

        mapper.releasePermission(SAFPath.build(primaryTreeUri, "Android", "data")) shouldBe true

        verify {
            contentResolver.releasePersistableUriPermission(
                primaryTreeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    @Test fun `getPermissions maps every persisted grant to a SAF path`() {
        every { contentResolver.persistedUriPermissions } returns listOf(
            permission(primaryTreeUri),
            permission(sdcardTreeUri),
        )
        val mapper = PathMapper(contentResolver, storageManager2)

        mapper.getPermissions() shouldContainExactly listOf(
            SAFPath.build(primaryTreeUri),
            SAFPath.build(sdcardTreeUri),
        )
    }

    @Test fun `hasPermission only knows the persisted uris`() {
        every { contentResolver.persistedUriPermissions } returns listOf(permission(primaryTreeUri))
        val mapper = PathMapper(contentResolver, storageManager2)

        mapper.hasPermission(primaryTreeUri) shouldBe true
        mapper.hasPermission(sdcardTreeUri) shouldBe false
    }

    @Test fun `hasPermission requires a read-write grant`() {
        val mapper = PathMapper(contentResolver, storageManager2)

        every { contentResolver.persistedUriPermissions } returns listOf(
            permission(primaryTreeUri, read = true, write = false),
        )
        mapper.hasPermission(primaryTreeUri) shouldBe false

        every { contentResolver.persistedUriPermissions } returns listOf(
            permission(primaryTreeUri, read = false, write = true),
        )
        mapper.hasPermission(primaryTreeUri) shouldBe false

        every { contentResolver.persistedUriPermissions } returns listOf(
            permission(primaryTreeUri, read = true, write = true),
        )
        mapper.hasPermission(primaryTreeUri) shouldBe true
    }

    @Test fun `takePermission upgrades a read-only grant`() {
        every { contentResolver.persistedUriPermissions } returns listOf(
            permission(primaryTreeUri, read = true, write = false),
        )
        every { contentResolver.takePersistableUriPermission(any(), any()) } just runs
        val mapper = PathMapper(contentResolver, storageManager2)

        mapper.takePermission(primaryTreeUri)

        verify {
            contentResolver.takePersistableUriPermission(
                primaryTreeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    @Test fun `takePermission upgrades a write-only grant`() {
        every { contentResolver.persistedUriPermissions } returns listOf(
            permission(primaryTreeUri, read = false, write = true),
        )
        every { contentResolver.takePersistableUriPermission(any(), any()) } just runs
        val mapper = PathMapper(contentResolver, storageManager2)

        mapper.takePermission(primaryTreeUri)

        verify {
            contentResolver.takePersistableUriPermission(
                primaryTreeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    @Test fun `a failed upgrade keeps the grant we already had`() {
        every { contentResolver.persistedUriPermissions } returns listOf(
            permission(primaryTreeUri, read = true, write = false),
        )
        every { contentResolver.takePersistableUriPermission(any(), any()) } throws SecurityException("nope")
        every { contentResolver.releasePersistableUriPermission(any(), any()) } just runs
        val mapper = PathMapper(contentResolver, storageManager2)

        shouldThrow<SecurityException> { mapper.takePermission(primaryTreeUri) }

        // Only the mode the failed take could have added is released, the pre-existing read survives.
        verify {
            contentResolver.releasePersistableUriPermission(
                primaryTreeUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        verify(exactly = 0) {
            contentResolver.releasePersistableUriPermission(
                primaryTreeUri,
                match { it and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0 },
            )
        }
    }

    @Test fun `a failed upgrade of a write-only grant only releases the read mode`() {
        every { contentResolver.persistedUriPermissions } returns listOf(
            permission(primaryTreeUri, read = false, write = true),
        )
        every { contentResolver.takePersistableUriPermission(any(), any()) } throws SecurityException("nope")
        every { contentResolver.releasePersistableUriPermission(any(), any()) } just runs
        val mapper = PathMapper(contentResolver, storageManager2)

        shouldThrow<SecurityException> { mapper.takePermission(primaryTreeUri) }

        verify {
            contentResolver.releasePersistableUriPermission(
                primaryTreeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        verify(exactly = 0) {
            contentResolver.releasePersistableUriPermission(
                primaryTreeUri,
                match { it and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 },
            )
        }
    }
}
