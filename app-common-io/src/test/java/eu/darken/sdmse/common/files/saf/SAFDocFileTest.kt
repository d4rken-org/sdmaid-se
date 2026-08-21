package eu.darken.sdmse.common.files.saf

import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.core.net.toUri
import eu.darken.sdmse.common.files.Ownership
import eu.darken.sdmse.common.files.Permissions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.saf.FakeDocumentsProvider
import testhelpers.saf.LegacyQueryShimProvider
import testhelpers.saf.SafTestHarness
import java.io.FileNotFoundException
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SAFDocFileTest : BaseTest() {

    @After
    fun tearDown() {
        // BaseTest's cleanup is a JUnit5 @AfterAll and doesn't run here.
        unmockkAll()
        LegacyQueryShimProvider.unregisterAll()
    }

    private fun harness(
        provider: FakeDocumentsProvider = FakeDocumentsProvider.tree(),
        granted: List<String> = emptyList(),
    ) = SafTestHarness(
        appScope = CoroutineScope(Dispatchers.Unconfined),
        provider = provider,
        grantedSegments = granted,
    )

    @Test
    fun `test tree uri no segments`() {
        SAFDocFile.buildTreeUri(
            Uri.parse("content://auth.ority/tree/primary%3A"),
            emptyList()
        ) shouldBe Uri.parse("content://auth.ority/tree/primary%3A/document/primary%3A")

        SAFDocFile.buildTreeUri(
            Uri.parse("content://auth.ority/tree/primary%3A"),
            emptyList()
        ).toString() shouldBe "content://auth.ority/tree/primary%3A/document/primary%3A"
    }

    @Test
    fun `test tree uri 1 segment`() {
        SAFDocFile.buildTreeUri(
            Uri.parse("content://auth.ority/tree/primary%3A"),
            listOf("segment1")
        ).toString() shouldBe "content://auth.ority/tree/primary%3A/document/primary%3A%2Fsegment1"
    }

    @Test
    fun `test tree uri 2 segments`() {
        SAFDocFile.buildTreeUri(
            Uri.parse("content://auth.ority/tree/primary%3A"),
            listOf("segment1", "segment2")
        ).toString() shouldBe "content://auth.ority/tree/primary%3A/document/primary%3A%2Fsegment1%2Fsegment2"
    }

    @Test
    fun `test tree uri 2 empty segment`() {
        SAFDocFile.buildTreeUri(
            Uri.parse("content://auth.ority/tree/primary%3A"),
            listOf("")
        ).toString() shouldBe "content://auth.ority/tree/primary%3A/document/primary%3A%2F"
    }

    @Test
    fun `test tree seperator addition`() {
        SAFDocFile.buildTreeUri(
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fdata"),
            listOf("com.samsung.android.smartmirroring")
        )
            .toString() shouldBe "content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fdata/document/primary%3AAndroid%2Fdata%2Fcom.samsung.android.smartmirroring"
    }

    @Test
    fun `test tree uri with repeated first crumb`() {
        // Separators are placed by position: a later crumb repeating the first one used to lose its
        // separator and get glued to its predecessor.
        SAFDocFile.buildTreeUri(
            Uri.parse("content://auth.ority/tree/primary%3A"),
            listOf("files", "cache", "files")
        ).toString() shouldBe "content://auth.ority/tree/primary%3A/document/primary%3A%2Ffiles%2Fcache%2Ffiles"
    }

    @Test
    fun `test tree uri with repeated non-first crumb`() {
        SAFDocFile.buildTreeUri(
            Uri.parse("content://auth.ority/tree/primary%3A"),
            listOf("files", "cache", "cache")
        ).toString() shouldBe "content://auth.ority/tree/primary%3A/document/primary%3A%2Ffiles%2Fcache%2Fcache"
    }

    @Test
    fun `fromTreeUri without PackageManager registration`() {
        val harness = harness()
        val strangerUri = "content://not.registered.authority/tree/root%3A".toUri()

        DocumentsContract.isDocumentUri(harness.context, strangerUri) shouldBe false

        SAFDocFile.fromTreeUri(harness.context, harness.contentResolver, strangerUri).uri shouldBe
                DocumentsContract.buildDocumentUriUsingTree(strangerUri, FakeDocumentsProvider.ROOT_ID)
    }

    @Test
    fun `fromTreeUri with PackageManager registration`() {
        val harness = harness()

        // A plain tree uri is not a document uri, registered or not, so both take the same branch.
        DocumentsContract.isDocumentUri(harness.context, harness.treeUri) shouldBe false
        SAFDocFile.fromTreeUri(harness.context, harness.contentResolver, harness.treeUri).uri shouldBe
                DocumentsContract.buildDocumentUriUsingTree(harness.treeUri, FakeDocumentsProvider.ROOT_ID)

        // A tree-scoped *document* uri only resolves as one because of the PackageManager registration.
        val docUri = harness.docUri("dir")
        DocumentsContract.isDocumentUri(harness.context, docUri) shouldBe true
        SAFDocFile.fromTreeUri(harness.context, harness.contentResolver, docUri).uri shouldBe docUri
    }

    @Test
    fun `name, exists, type, length and lastModified`() {
        val harness = harness(
            FakeDocumentsProvider.tree {
                dir("dir", lastModified = 1000L)
                file("dir/file.txt", content = "12345", lastModified = 2000L)
            }
        )

        harness.docFile("dir", "file.txt").apply {
            name shouldBe "file.txt"
            exists shouldBe true
            isFile shouldBe true
            isDirectory shouldBe false
            length shouldBe 5L
            lastModified shouldBe Instant.ofEpochMilli(2000L)
        }

        harness.docFile("dir").apply {
            name shouldBe "dir"
            exists shouldBe true
            isFile shouldBe false
            isDirectory shouldBe true
            lastModified shouldBe Instant.ofEpochMilli(1000L)
        }

        harness.docFile("dir", "nope.txt").apply {
            exists shouldBe false
            name.shouldBeNull()
            isFile shouldBe false
            isDirectory shouldBe false
            length shouldBe 0L
            lastModified shouldBe Instant.ofEpochMilli(0L)
        }
    }

    @Test
    fun `listFiles returns the children`() {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("dir/one.txt")
                file("dir/two.txt")
                dir("dir/sub")
                file("other.txt")
            }
        )

        harness.docFile("dir").listFiles().map { it.uri } shouldContainExactly listOf(
            harness.docUri("dir", "one.txt"),
            harness.docUri("dir", "two.txt"),
            harness.docUri("dir", "sub"),
        )
    }

    @Test
    fun `hasChildren answers from the child cursor alone`() {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("populated/file.txt")
                dir("empty")
            }
        )

        harness.docFile("populated").hasChildren() shouldBe true
        harness.docFile("empty").hasChildren() shouldBe false
    }

    @Test
    fun `hasChildren does not query the children themselves`() {
        // A directory whose children can't be looked up individually is still non-empty. Answering
        // this with lookupFiles() would turn a vanishing or broken child into an unrelated failure.
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })
        harness.provider.failDocQueryFor["root:dir/file.txt"] = RuntimeException("provider is having a bad day")

        harness.docFile("dir").hasChildren() shouldBe true
    }

    @Test
    fun `hasChildren propagates a failing query`() {
        // "We couldn't ask" must not read as "the directory is empty".
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })
        harness.provider.failChildQueryFor["root:dir"] = RuntimeException("provider is having a bad day")

        shouldThrow<RuntimeException> { harness.docFile("dir").hasChildren() }
    }

    @Test
    fun `listChildDisplayNames returns every child name`() {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("dir/one.txt")
                file("dir/two.txt")
                dir("dir/sub")
                file("other.txt")
            }
        )

        harness.docFile("dir").listChildDisplayNames() shouldBe setOf("one.txt", "two.txt", "sub")
        harness.docFile("dir", "sub").listChildDisplayNames() shouldBe emptySet()
    }

    @Test
    fun `listChildDisplayNames skips rows without a display name`() {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("dir/named.txt")
                file("dir/nameless.txt", nullDisplayName = true)
            }
        )

        harness.docFile("dir").listChildDisplayNames() shouldBe setOf("named.txt")
    }

    @Test
    fun `listChildDisplayNames answers for a name that two children share`() {
        // findFile() hands back null for such a name (singleOrNull), which reads as "still free".
        val harness = harness(FakeDocumentsProvider.tree { file("dir/twin.txt") })
        harness.provider.addNode(
            FakeDocumentsProvider.Node(
                documentId = "root:dir/twin-second",
                parentId = "root:dir",
                displayName = "twin.txt",
                mimeType = "application/octet-stream",
            )
        )

        harness.docFile("dir").listChildDisplayNames() shouldBe setOf("twin.txt")
        harness.docFile("dir").findFile("twin.txt").shouldBeNull()
    }

    @Test
    fun `listChildDisplayNames is the snapshot the cursor was built from`() {
        // A child that vanishes while the listing is consumed stays in the answer, i.e. the result
        // is advice about a point in time, never a guarantee about the directory right now.
        val harness = harness(FakeDocumentsProvider.tree { file("dir/gone.txt") })
        harness.provider.onChildQuery = { harness.provider.removeNode("dir", "gone.txt") }

        harness.docFile("dir").listChildDisplayNames() shouldBe setOf("gone.txt")
        harness.provider.hasNode("dir", "gone.txt") shouldBe false
    }

    @Test
    fun `listChildDisplayNames propagates a failing query`() {
        // "We couldn't ask" must not read as "every name is free".
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })
        harness.provider.failChildQueryFor["root:dir"] = RuntimeException("provider is having a bad day")

        shouldThrow<RuntimeException> { harness.docFile("dir").listChildDisplayNames() }
    }

    @Test
    fun `existsStrict propagates what exists swallows`() {
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })
        val docFile = harness.docFile("dir", "file.txt")

        docFile.existsStrict() shouldBe true
        harness.docFile("dir", "ghost.txt").existsStrict() shouldBe false

        harness.provider.failNextQueryWith = RuntimeException("provider is having a bad day")
        shouldThrow<RuntimeException> { docFile.existsStrict() }
    }

    @Test
    fun `readDisplayNameStrict propagates what name swallows`() {
        // "We couldn't ask" must not read as "the provider supplies no display name", otherwise a
        // caller that verifies the name it asked for accepts whatever it got instead.
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("dir/file.txt")
                file("dir/nameless.txt", nullDisplayName = true)
            }
        )
        val docFile = harness.docFile("dir", "file.txt")

        docFile.readDisplayNameStrict() shouldBe "file.txt"
        // Only a row that really carries no name answers null.
        harness.docFile("dir", "nameless.txt").readDisplayNameStrict().shouldBeNull()

        harness.provider.failNextQueryWith = RuntimeException("provider is having a bad day")
        shouldThrow<RuntimeException> { docFile.readDisplayNameStrict() }

        // A document that isn't there has no name to hand out either, which is not the same as null.
        shouldThrow<Exception> { harness.docFile("dir", "ghost.txt").readDisplayNameStrict() }
    }

    @Test
    fun `findFile matches by display name`() {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("dir/wanted.txt")
                file("dir/other.txt")
            }
        )

        harness.docFile("dir").findFile("wanted.txt")!!.uri shouldBe harness.docUri("dir", "wanted.txt")
    }

    @Test
    fun `findFile returns null for an empty directory`() {
        val harness = harness(FakeDocumentsProvider.tree { dir("dir") })

        harness.docFile("dir").findFile("wanted.txt").shouldBeNull()
    }

    @Test
    fun `findFile returns null when no row carries the wanted display name`() {
        // Provider side filtering is not guaranteed - DocumentsProvider ignores the SQL selection and
        // returns every child, so the name match happens in SAFDocFile.
        val harness = harness(FakeDocumentsProvider.tree { file("dir/actually-different.txt") })

        harness.docFile("dir").findFile("wanted.txt").shouldBeNull()
    }

    @Test
    fun `createFile and createDirectory`() {
        val harness = harness(FakeDocumentsProvider.tree { dir("dir") })

        val newFile = harness.docFile("dir").createFile("application/octet-stream", "new.bin")
        newFile.uri shouldBe harness.docUri("dir", "new.bin")
        harness.provider.hasNode("dir", "new.bin") shouldBe true

        val newDir = harness.docFile("dir").createDirectory("newdir")
        newDir.uri shouldBe harness.docUri("dir", "newdir")
        harness.provider.node("dir", "newdir")!!.mimeType shouldBe Document.MIME_TYPE_DIR

        harness.provider.createdDocuments shouldContainExactly listOf(
            "root:dir" to "new.bin",
            "root:dir" to "newdir",
        )
    }

    @Test
    fun `delete removes the document`() {
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })

        harness.docFile("dir", "file.txt").delete() shouldBe true

        // Asserting the provider tree, not !exists: a failing query also reports exists == false.
        harness.provider.hasNode("dir", "file.txt") shouldBe false
        harness.provider.deletedDocuments shouldContainExactly listOf("root:dir/file.txt")
    }

    @Test
    fun `delete swallows an IllegalArgumentException that mentions FileNotFoundException`() {
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })
        harness.provider.failNextDeleteWith = IllegalArgumentException("java.io.FileNotFoundException: gone")

        harness.docFile("dir", "file.txt").delete() shouldBe false

        harness.provider.hasNode("dir", "file.txt") shouldBe true
    }

    @Test
    fun `delete rethrows an IllegalArgumentException that does not mention FileNotFoundException`() {
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt") })
        harness.provider.failNextDeleteWith = IllegalArgumentException("something else")

        shouldThrow<IllegalArgumentException> { harness.docFile("dir", "file.txt").delete() }
    }

    @Test
    fun `delete lets a real FileNotFoundException escape`() {
        // documents suspect behavior: SAFDocFile.delete only catches IllegalArgumentException, but
        // DocumentsContract.deleteDocument rethrows the provider's FileNotFoundException on API 26+.
        // Containing this is SAFGateway.delete's job (see D9).
        val harness = harness(FakeDocumentsProvider.tree { dir("dir") })

        shouldThrow<FileNotFoundException> { harness.docFile("dir", "ghost.txt").delete() }
    }

    @Test
    fun `setLastModified can not succeed against a conforming provider`() {
        // documents suspect behavior (D5): DocumentsProvider.update is final and throws, and there is
        // no DocumentsContract API to set a modification time, so this always reports failure.
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt", lastModified = 1000L) })

        harness.docFile("dir", "file.txt").setLastModified(Instant.ofEpochMilli(9999L)) shouldBe false

        harness.provider.node("dir", "file.txt")!!.lastModified shouldBe 1000L
    }

    @Test
    fun `readable truth table`() {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("dir/file.txt")
            }
        )

        // Grant allows read + document has a mime type
        harness.docFile("dir", "file.txt").readable shouldBe true

        // Document has no mime type (it doesn't exist)
        harness.docFile("dir", "ghost.txt").readable shouldBe false

        // Grant denies read
        harness.docFile("dir", "file.txt", context = harness.deniedUriPermissionContext()).readable shouldBe false
    }

    @Test
    fun `writable truth table`() {
        val harness = harness(
            FakeDocumentsProvider.tree {
                file("deletable", flags = Document.FLAG_SUPPORTS_DELETE)
                file("writable", flags = Document.FLAG_SUPPORTS_WRITE)
                file("inert", flags = 0)
                dir("createable", flags = Document.FLAG_DIR_SUPPORTS_CREATE)
                dir("inertdir", flags = 0)
            }
        )

        harness.docFile("deletable").writable shouldBe true
        harness.docFile("writable").writable shouldBe true
        harness.docFile("createable").writable shouldBe true
        harness.docFile("inert").writable shouldBe false
        harness.docFile("inertdir").writable shouldBe false

        // Document has no mime type (it doesn't exist)
        harness.docFile("ghost").writable shouldBe false

        // Grant denies write
        harness.docFile("deletable", context = harness.deniedUriPermissionContext()).writable shouldBe false
    }

    @Test
    fun `query errors are swallowed into null`() {
        val harness = harness(FakeDocumentsProvider.tree { file("dir/file.txt", content = "12345") })
        val docFile = harness.docFile("dir", "file.txt")

        harness.provider.failNextQueryWith = RuntimeException("provider is having a bad day")
        docFile.name.shouldBeNull()

        harness.provider.failNextQueryWith = RuntimeException("provider is having a bad day")
        docFile.length shouldBe 0L

        // This is why deletion must never be proven via exists: a broken provider looks like an empty one.
        harness.provider.failNextQueryWith = RuntimeException("provider is having a bad day")
        docFile.exists shouldBe false
        harness.provider.hasNode("dir", "file.txt") shouldBe true
    }

    @Test
    fun `setPermissions propagates a failure to open the descriptor`() {
        // openPFD sits outside the try in SAFDocFile.setPermissions, so this does not return false.
        val harness = harness(
            FakeDocumentsProvider.tree {
                dir("dir")
                file("unopenable.txt", openable = false)
            }
        )

        shouldThrow<FileNotFoundException> { harness.docFile("dir").setPermissions(Permissions(0b111000000)) }
        shouldThrow<FileNotFoundException> {
            harness.docFile("unopenable.txt").setPermissions(Permissions(0b111000000))
        }
    }

    @Test
    fun `setPermissions returns false when fchmod fails`() {
        val harness = harness(FakeDocumentsProvider.tree { file("file.txt", content = "12345") })
        val permissions = Permissions(0b111000000)
        mockkStatic(Os::class)
        every { Os.fchmod(any(), permissions.mode) } throws ErrnoException("fchmod", OsConstants.EPERM)

        harness.docFile("file.txt").setPermissions(permissions) shouldBe false

        verify { Os.fchmod(any(), permissions.mode) }
    }

    @Test
    fun `setPermissions returns true when fchmod succeeds`() {
        val harness = harness(FakeDocumentsProvider.tree { file("file.txt", content = "12345") })
        val permissions = Permissions(0b111000000)
        mockkStatic(Os::class)
        every { Os.fchmod(any(), permissions.mode) } just runs

        harness.docFile("file.txt").setPermissions(permissions) shouldBe true

        verify { Os.fchmod(any(), permissions.mode) }
    }

    @Test
    fun `setOwnership propagates a failure to open the descriptor`() {
        // openPFD sits outside the try in SAFDocFile.setOwnership too.
        val harness = harness(
            FakeDocumentsProvider.tree {
                dir("dir")
                file("unopenable.txt", openable = false)
            }
        )

        shouldThrow<FileNotFoundException> { harness.docFile("dir").setOwnership(Ownership(1000L, 1000L)) }
        shouldThrow<FileNotFoundException> {
            harness.docFile("unopenable.txt").setOwnership(Ownership(1000L, 1000L))
        }
    }

    @Test
    fun `setOwnership returns false when fchown fails`() {
        val harness = harness(FakeDocumentsProvider.tree { file("file.txt", content = "12345") })
        val ownership = Ownership(1000L, 1000L)
        mockkStatic(Os::class)
        every {
            Os.fchown(any(), ownership.userId.toInt(), ownership.groupId.toInt())
        } throws ErrnoException("fchown", OsConstants.EPERM)

        harness.docFile("file.txt").setOwnership(ownership) shouldBe false

        verify { Os.fchown(any(), ownership.userId.toInt(), ownership.groupId.toInt()) }
    }

    @Test
    fun `setOwnership returns true when fchown succeeds`() {
        val harness = harness(FakeDocumentsProvider.tree { file("file.txt", content = "12345") })
        val ownership = Ownership(1000L, 1000L)
        mockkStatic(Os::class)
        every { Os.fchown(any(), ownership.userId.toInt(), ownership.groupId.toInt()) } just runs

        harness.docFile("file.txt").setOwnership(ownership) shouldBe true

        verify { Os.fchown(any(), ownership.userId.toInt(), ownership.groupId.toInt()) }
    }

    @Test
    fun `fstat returns null when the descriptor can not be opened`() {
        val harness = harness(
            FakeDocumentsProvider.tree {
                dir("dir")
                file("unopenable.txt", openable = false)
            }
        )

        harness.docFile("dir").fstat().shouldBeNull()
        harness.docFile("unopenable.txt").fstat().shouldBeNull()
    }

    @Test
    fun `fstat returns a struct for an openable document`() {
        // The struct's contents are deliberately not asserted: Robolectric does not back the Os.*
        // syscalls, Os.fstat().st_size returns 0 no matter how large the backing file is.
        val harness = harness(FakeDocumentsProvider.tree { file("file.txt", content = "12345") })

        harness.docFile("file.txt").fstat().shouldNotBeNull()
    }
}
