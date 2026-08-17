package eu.darken.sdmse.common.files.saf

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/**
 * An in-memory [DocumentsProvider] that behaves like a real SAF provider for the operations
 * [SAFGateway] and [SAFDocFile] use.
 *
 * Document ids are path based, mirroring `ExternalStorageProvider`: [ROOT_ID] is `root:` and a
 * document below it is `root:<segment>/<segment>`. Ids are normalized on the way in, so a caller
 * that builds `root:/a/b` (which [SAFDocFile.buildTreeUri] does for a grant on the volume root)
 * resolves to the same node as `root:a/b`.
 *
 * Register it through [SafTestHarness], which wires it behind [LegacyQueryShimProvider].
 */
class FakeDocumentsProvider : DocumentsProvider() {

    /**
     * What deleting a directory does. Real providers differ, so every test states which one it runs
     * under instead of relying on an incidental default.
     */
    enum class DirDeleteMode {
        /** Deleting a directory removes its whole subtree, like `ExternalStorageProvider` does. */
        CASCADE,

        /** Deleting a non-empty directory fails, modelling a stricter provider. */
        REJECT_NONEMPTY,
    }

    data class Node(
        val documentId: String,
        val displayName: String?,
        val mimeType: String,
        val size: Long = 0L,
        val lastModified: Long = 0L,
        val flags: Int = 0,
        val file: File? = null,
    ) {
        val isDirectory: Boolean
            get() = mimeType == Document.MIME_TYPE_DIR
    }

    /** Insertion ordered, so child enumeration order is deterministic. */
    private val nodes = LinkedHashMap<String, Node>()

    var dirDeleteMode: DirDeleteMode = DirDeleteMode.CASCADE

    /** Fails the next query of any kind, then clears itself. */
    var failNextQueryWith: Exception? = null

    /** Fails every document query for these (normalized) document ids. */
    val failDocQueryFor = mutableMapOf<String, Exception>()

    /** Fails every child listing for these (normalized) document ids. */
    val failChildQueryFor = mutableMapOf<String, Exception>()

    /** Fails the next [deleteDocument], then clears itself. */
    var failNextDeleteWith: Exception? = null

    /**
     * Invoked with the parent document id *after* a child listing cursor has been built, so a test
     * can mutate the tree behind a consumer that already holds the listing.
     */
    var onChildQuery: ((String) -> Unit)? = null

    val queriedDocuments = mutableListOf<String>()
    val queriedChildren = mutableListOf<String>()
    val createdDocuments = mutableListOf<Pair<String, String>>()
    val deletedDocuments = mutableListOf<String>()
    val openedDocuments = mutableListOf<Pair<String, String>>()

    init {
        nodes[ROOT_ID] = Node(
            documentId = ROOT_ID,
            displayName = "root",
            mimeType = Document.MIME_TYPE_DIR,
            flags = DEFAULT_DIR_FLAGS,
        )
    }

    override fun onCreate(): Boolean = true

    // ---------------------------------------------------------------- tree access for assertions

    fun node(vararg segments: String): Node? = nodes[docIdOf(segments.toList())]

    fun hasNode(vararg segments: String): Boolean = node(*segments) != null

    /** All document ids currently in the tree, root first. */
    fun documentIds(): List<String> = nodes.keys.toList()

    fun addNode(node: Node) {
        nodes[normalize(node.documentId)] = node
    }

    fun removeNode(vararg segments: String) {
        nodes.remove(docIdOf(segments.toList()))
    }

    fun resetInstrumentation() {
        queriedDocuments.clear()
        queriedChildren.clear()
        createdDocuments.clear()
        deletedDocuments.clear()
        openedDocuments.clear()
    }

    // ---------------------------------------------------------------------------- DocumentsProvider

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE))
        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, "root")
            add(Root.COLUMN_DOCUMENT_ID, ROOT_ID)
            add(Root.COLUMN_TITLE, "FakeDocumentsProvider")
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        consumeQueryFailure()
        val id = normalize(documentId)
        queriedDocuments.add(id)
        failDocQueryFor[id]?.let { throw it }
        val node = nodes[id] ?: throw FileNotFoundException("No such document: $id")
        return cursorFor(projection, listOf(node))
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        consumeQueryFailure()
        val id = normalize(parentDocumentId)
        queriedChildren.add(id)
        failChildQueryFor[id]?.let { throw it }
        val parent = nodes[id] ?: throw FileNotFoundException("No such document: $id")
        if (!parent.isDirectory) throw FileNotFoundException("Not a directory: $id")
        val cursor = cursorFor(projection, childrenOf(id))
        onChildQuery?.invoke(id)
        return cursor
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parentId = normalize(parentDocumentId)
        createdDocuments.add(parentId to displayName)
        val parent = nodes[parentId] ?: throw FileNotFoundException("No such document: $parentId")
        if (!parent.isDirectory) throw FileNotFoundException("Not a directory: $parentId")

        val childId = childIdOf(parentId, displayName)
        if (nodes.containsKey(childId)) throw FileNotFoundException("Already exists: $childId")

        val isDir = mimeType == Document.MIME_TYPE_DIR
        nodes[childId] = Node(
            documentId = childId,
            displayName = displayName,
            mimeType = mimeType,
            flags = if (isDir) DEFAULT_DIR_FLAGS else DEFAULT_FILE_FLAGS,
            file = if (isDir) null else newBackingFile(""),
        )
        return childId
    }

    override fun deleteDocument(documentId: String) {
        failNextDeleteWith?.let {
            failNextDeleteWith = null
            throw it
        }
        val id = normalize(documentId)
        val node = nodes[id] ?: throw FileNotFoundException("No such document: $id")

        if (node.isDirectory) {
            val descendants = nodes.keys.filter { isDescendant(id, it) }
            when (dirDeleteMode) {
                DirDeleteMode.CASCADE -> descendants.forEach { drop(it) }
                DirDeleteMode.REJECT_NONEMPTY -> if (descendants.isNotEmpty()) {
                    throw IllegalStateException("Directory not empty: $id")
                }
            }
        }

        drop(id)
        deletedDocuments.add(id)
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val id = normalize(documentId)
        openedDocuments.add(id to mode)
        val node = nodes[id] ?: throw FileNotFoundException("No such document: $id")
        val file = node.file ?: throw FileNotFoundException("Document has no content: $id")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        // Path based like ExternalStorageProvider, so it also answers for documents that don't exist.
        return isDescendant(normalize(parentDocumentId), normalize(documentId))
    }

    // -------------------------------------------------------------------------------- internals

    private fun consumeQueryFailure() {
        failNextQueryWith?.let {
            failNextQueryWith = null
            throw it
        }
    }

    private fun drop(documentId: String) {
        nodes.remove(documentId)?.file?.delete()
    }

    private fun childrenOf(parentId: String): List<Node> {
        val parentSegments = segmentsOf(parentId)
        return nodes.values.filter {
            val segments = segmentsOf(it.documentId)
            segments.size == parentSegments.size + 1 && segments.dropLast(1) == parentSegments
        }
    }

    private fun cursorFor(projection: Array<out String>?, rows: List<Node>): MatrixCursor {
        val columns = projection ?: DOCUMENT_PROJECTION
        val cursor = MatrixCursor(Array(columns.size) { columns[it] })
        rows.forEach { node ->
            val row = cursor.newRow()
            columns.forEach { column -> row.add(column, valueFor(node, column)) }
        }
        return cursor
    }

    private fun valueFor(node: Node, column: String): Any? = when (column) {
        Document.COLUMN_DOCUMENT_ID -> node.documentId
        Document.COLUMN_DISPLAY_NAME -> node.displayName
        Document.COLUMN_MIME_TYPE -> node.mimeType
        Document.COLUMN_SIZE -> node.file?.length() ?: node.size
        Document.COLUMN_LAST_MODIFIED -> node.lastModified
        Document.COLUMN_FLAGS -> node.flags
        else -> null
    }

    /** Declares the tree shape a test needs, creating missing ancestors as directories. */
    inner class Builder internal constructor() {

        fun dir(
            path: String,
            flags: Int = DEFAULT_DIR_FLAGS,
            lastModified: Long = 0L,
        ): Node = put(
            segments = pathSegments(path),
            mimeType = Document.MIME_TYPE_DIR,
            flags = flags,
            lastModified = lastModified,
            size = 0L,
            content = null,
            nullDisplayName = false,
        )

        fun file(
            path: String,
            content: String? = null,
            size: Long = 0L,
            mimeType: String = "application/octet-stream",
            flags: Int = DEFAULT_FILE_FLAGS,
            lastModified: Long = 0L,
            nullDisplayName: Boolean = false,
        ): Node = put(
            segments = pathSegments(path),
            mimeType = mimeType,
            flags = flags,
            lastModified = lastModified,
            size = size,
            content = content,
            nullDisplayName = nullDisplayName,
        )

        private fun put(
            segments: List<String>,
            mimeType: String,
            flags: Int,
            lastModified: Long,
            size: Long,
            content: String?,
            nullDisplayName: Boolean,
        ): Node {
            require(segments.isNotEmpty()) { "Can't replace the root node" }
            segments.dropLast(1).indices.forEach { index ->
                val ancestor = segments.take(index + 1)
                val ancestorId = docIdOf(ancestor)
                if (!nodes.containsKey(ancestorId)) {
                    nodes[ancestorId] = Node(
                        documentId = ancestorId,
                        displayName = ancestor.last(),
                        mimeType = Document.MIME_TYPE_DIR,
                        flags = DEFAULT_DIR_FLAGS,
                    )
                }
            }
            val documentId = docIdOf(segments)
            val node = Node(
                documentId = documentId,
                displayName = if (nullDisplayName) null else segments.last(),
                mimeType = mimeType,
                size = size,
                lastModified = lastModified,
                flags = flags,
                file = content?.let { newBackingFile(it) },
            )
            nodes[documentId] = node
            return node
        }
    }

    companion object {
        const val ROOT_ID = "root:"

        const val DEFAULT_DIR_FLAGS = Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_DELETE
        const val DEFAULT_FILE_FLAGS = Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE

        private val DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
        )

        fun tree(block: FakeDocumentsProvider.Builder.() -> Unit = {}): FakeDocumentsProvider =
            FakeDocumentsProvider().also { it.Builder().block() }

        fun pathSegments(path: String): List<String> = path.split('/').filter { it.isNotEmpty() }

        fun segmentsOf(documentId: String): List<String> =
            documentId.substringAfter(':').split('/').filter { it.isNotEmpty() }

        fun docIdOf(segments: List<String>): String = ROOT_ID + segments.joinToString("/")

        fun normalize(documentId: String): String = docIdOf(segmentsOf(documentId))

        private fun childIdOf(parentId: String, name: String): String = docIdOf(segmentsOf(parentId) + name)

        private fun isDescendant(parentId: String, documentId: String): Boolean {
            val parent = segmentsOf(parentId)
            val child = segmentsOf(documentId)
            return child.size > parent.size && child.take(parent.size) == parent
        }

        private fun newBackingFile(content: String): File {
            val dir = File("build/tmp/unit_tests/saf-harness").apply { mkdirs() }
            return File.createTempFile("fakedoc", ".bin", dir).apply {
                deleteOnExit()
                writeText(content)
            }
        }
    }
}
