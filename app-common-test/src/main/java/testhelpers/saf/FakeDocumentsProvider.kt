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
 * The tree is held as nodes with an explicit parent link, so child enumeration never depends on the
 * shape of a document id. Which ids are handed out is [IdMode]'s job.
 *
 * Register it through [SafTestHarness], which wires it behind [LegacyQueryShimProvider].
 */
class FakeDocumentsProvider(
    val idMode: IdMode = IdMode.PATH_DERIVED,
    /**
     * Mints opaque ids for [Builder] fixture nodes too, so that nothing below the tree root can be
     * addressed by a rebuilt id. Only the root document keeps [ROOT_ID], which is what the granted
     * tree uri points at. Requires [IdMode.OPAQUE].
     */
    val opaqueFixtureIds: Boolean = false,
) : DocumentsProvider() {

    /**
     * How ids for newly created documents are minted. Real providers differ, and a caller must not
     * be able to reconstruct an id instead of using the one the provider handed back.
     */
    enum class IdMode {
        /**
         * Path based like `ExternalStorageProvider`: [ROOT_ID] is `root:` and a document below it is
         * `root:<segment>/<segment>`. Ids are normalized on the way in, so a caller that builds
         * `root:/a/b` (which [SAFDocFile.buildTreeUri] does for a grant on the volume root) resolves
         * to the same node as `root:a/b`.
         */
        PATH_DERIVED,

        /**
         * Created documents get ids like `doc-1` that carry no path information at all. Fixture nodes
         * declared through [Builder] keep their path based ids, because that is what the granted tree
         * uri addresses, unless [opaqueFixtureIds] says otherwise.
         */
        OPAQUE,
    }

    /**
     * What deleting a directory does. Real providers differ, so every test states which one it runs
     * under instead of relying on an incidental default.
     */
    enum class DirDeleteMode {
        /** Deleting a directory removes its whole subtree, like `ExternalStorageProvider` does. */
        CASCADE,

        /** Deleting a non-empty directory fails, modelling a stricter provider. */
        REJECT_NONEMPTY,

        /**
         * The first directory delete removes the childless documents below the target and then fails,
         * modelling a cascade that gives up part way through. Afterwards the provider behaves like
         * [REJECT_NONEMPTY], so whatever cleans up the remains has to work bottom up.
         */
        CASCADE_PARTIAL_FAILURE,
    }

    data class Node(
        val documentId: String,
        /** The document this node hangs beneath, null only for the tree root. */
        val parentId: String?,
        val displayName: String?,
        val mimeType: String,
        /** Explicit `COLUMN_SIZE` override, otherwise the backing file's length is reported. */
        val size: Long? = null,
        val lastModified: Long = 0L,
        val flags: Int = 0,
        val file: File? = null,
    ) {
        val isDirectory: Boolean
            get() = mimeType == Document.MIME_TYPE_DIR
    }

    /** Insertion ordered, so child enumeration order is deterministic. */
    private val nodes = LinkedHashMap<String, Node>()

    /** Path based id -> the id the fixture node actually got, only used under [opaqueFixtureIds]. */
    private val fixtureIds = mutableMapOf<String, String>()

    private var opaqueIdCounter = 0

    var dirDeleteMode: DirDeleteMode = DirDeleteMode.CASCADE

    /** Fails the next query of any kind, then clears itself. */
    var failNextQueryWith: Exception? = null

    /** Fails every document query for these (normalized) document ids. */
    val failDocQueryFor = mutableMapOf<String, Exception>()

    /** Fails every child listing for these (normalized) document ids. */
    val failChildQueryFor = mutableMapOf<String, Exception>()

    /** Fails the next [deleteDocument] before it deletes anything, then clears itself. */
    var failNextDeleteWith: Exception? = null

    /**
     * Fails the next [deleteDocument] *after* it deleted, then clears itself. Models a provider that
     * reports a failure for a deletion that actually went through.
     */
    var failNextDeleteAfterwardsWith: Exception? = null

    /**
     * Creates the next document under this display name instead of the requested one, then clears
     * itself. Models a provider that sanitizes or uniquifies names, like `ExternalStorageProvider`.
     */
    var renameNextCreatedTo: String? = null

    /**
     * Invoked with the parent document id *after* a child listing cursor has been built, so a test
     * can mutate the tree behind a consumer that already holds the listing.
     */
    var onChildQuery: ((String) -> Unit)? = null

    /** Invoked with the document id *after* a document cursor has been built, see [onChildQuery]. */
    var onDocumentQuery: ((String) -> Unit)? = null

    /** Invoked with the document id at the start of [deleteDocument], before any failure hook fires. */
    var onDelete: ((String) -> Unit)? = null

    val queriedDocuments = mutableListOf<String>()
    val queriedChildren = mutableListOf<String>()

    /** Every [deleteDocument] call, including the ones that end up failing. */
    val deleteCalls = mutableListOf<String>()

    /** Parent document id and *requested* display name, per [createDocument] call. */
    val createdDocuments = mutableListOf<Pair<String, String>>()

    /** The ids handed back by [createDocument], in the same order as [createdDocuments]. */
    val createdDocumentIds = mutableListOf<String>()
    val deletedDocuments = mutableListOf<String>()
    val openedDocuments = mutableListOf<Pair<String, String>>()

    init {
        require(!opaqueFixtureIds || idMode == IdMode.OPAQUE) {
            "opaqueFixtureIds only makes sense together with IdMode.OPAQUE"
        }
        nodes[ROOT_ID] = Node(
            documentId = ROOT_ID,
            parentId = null,
            displayName = "root",
            mimeType = Document.MIME_TYPE_DIR,
            flags = DEFAULT_DIR_FLAGS,
        )
    }

    override fun onCreate(): Boolean = true

    // ---------------------------------------------------------------- tree access for assertions

    /**
     * Path based lookup, i.e. only meaningful for [IdMode.PATH_DERIVED] and for fixture nodes that
     * kept a path based id. Use [resolve] under [opaqueFixtureIds].
     */
    fun node(vararg segments: String): Node? = nodes[docIdOf(segments.toList())]

    fun hasNode(vararg segments: String): Boolean = node(*segments) != null

    fun nodeById(documentId: String): Node? = nodes[normalize(documentId)]

    /** Follows the explicit parent links by display name, so it works in either [IdMode]. */
    fun resolve(vararg segments: String): Node? = segments.fold(nodes[ROOT_ID]) { parent, name ->
        parent?.let { p -> childrenOf(p.documentId).firstOrNull { it.displayName == name } }
    }

    fun children(documentId: String): List<Node> = childrenOf(normalize(documentId))

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
        createdDocumentIds.clear()
        deleteCalls.clear()
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
        val cursor = cursorFor(projection, listOf(node))
        onDocumentQuery?.invoke(id)
        return cursor
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

        val effectiveName = renameNextCreatedTo?.also { renameNextCreatedTo = null } ?: displayName
        if (childrenOf(parentId).any { it.displayName == effectiveName }) {
            throw FileNotFoundException("Already exists: $effectiveName below $parentId")
        }

        val childId = when (idMode) {
            IdMode.PATH_DERIVED -> childIdOf(parentId, effectiveName)
            IdMode.OPAQUE -> "$OPAQUE_ID_PREFIX${++opaqueIdCounter}"
        }
        if (nodes.containsKey(childId)) throw FileNotFoundException("Already exists: $childId")

        val isDir = mimeType == Document.MIME_TYPE_DIR
        nodes[childId] = Node(
            documentId = childId,
            parentId = parentId,
            displayName = effectiveName,
            mimeType = mimeType,
            flags = if (isDir) DEFAULT_DIR_FLAGS else DEFAULT_FILE_FLAGS,
            file = if (isDir) null else newBackingFile(""),
        )
        createdDocumentIds.add(childId)
        return childId
    }

    override fun deleteDocument(documentId: String) {
        val id = normalize(documentId)
        deleteCalls.add(id)
        onDelete?.invoke(id)

        failNextDeleteWith?.let {
            failNextDeleteWith = null
            throw it
        }

        val node = nodes[id] ?: throw FileNotFoundException("No such document: $id")

        if (node.isDirectory) {
            val descendants = descendantsOf(id)
            when (dirDeleteMode) {
                DirDeleteMode.CASCADE -> descendants.forEach { drop(it) }

                DirDeleteMode.REJECT_NONEMPTY -> if (descendants.isNotEmpty()) {
                    throw IllegalStateException("Directory not empty: $id")
                }

                DirDeleteMode.CASCADE_PARTIAL_FAILURE -> {
                    dirDeleteMode = DirDeleteMode.REJECT_NONEMPTY
                    descendants.filter { childrenOf(it).isEmpty() }.forEach { drop(it) }
                    throw IllegalStateException("Gave up while deleting $id")
                }
            }
        }

        drop(id)
        deletedDocuments.add(id)

        failNextDeleteAfterwardsWith?.let {
            failNextDeleteAfterwardsWith = null
            throw it
        }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val id = normalize(documentId)
        openedDocuments.add(id to mode)
        val node = nodes[id] ?: throw FileNotFoundException("No such document: $id")
        val file = node.file ?: throw FileNotFoundException("Document has no content: $id")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parentId = normalize(parentDocumentId)
        val childId = normalize(documentId)
        return when (idMode) {
            // Path based like ExternalStorageProvider, so it also answers for documents that don't exist.
            IdMode.PATH_DERIVED -> isDescendant(parentId, childId)
            // Opaque ids say nothing about ancestry, only the links of existing nodes can answer.
            IdMode.OPAQUE -> ancestorsOf(childId).any { it == parentId }
        }
    }

    // -------------------------------------------------------------------------------- internals

    /** The id a fixture node gets, minted once per path so parent links stay consistent. */
    private fun fixtureId(segments: List<String>): String {
        val pathId = docIdOf(segments)
        if (!opaqueFixtureIds || segments.isEmpty()) return pathId
        return fixtureIds.getOrPut(pathId) { "$OPAQUE_ID_PREFIX${++opaqueIdCounter}" }
    }

    private fun consumeQueryFailure() {
        failNextQueryWith?.let {
            failNextQueryWith = null
            throw it
        }
    }

    private fun drop(documentId: String) {
        nodes.remove(documentId)?.file?.delete()
    }

    private fun childrenOf(parentId: String): List<Node> = nodes.values.filter { it.parentId == parentId }

    private fun ancestorsOf(documentId: String): Sequence<String> =
        generateSequence(nodes[documentId]?.parentId) { nodes[it]?.parentId }

    private fun descendantsOf(parentId: String): List<String> = nodes.values
        .filter { it.documentId != parentId && ancestorsOf(it.documentId).any { id -> id == parentId } }
        .map { it.documentId }

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
        Document.COLUMN_SIZE -> node.size ?: node.file?.length() ?: 0L
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
            size = null,
            content = null,
            nullDisplayName = false,
            openable = false,
        )

        /**
         * A file document. It gets a backing temp file unless [openable] says otherwise, so it can be
         * opened like any real non-virtual document. [size] overrides what `COLUMN_SIZE` reports,
         * which is the backing file's length by default.
         */
        fun file(
            path: String,
            content: String? = null,
            size: Long? = null,
            mimeType: String = "application/octet-stream",
            flags: Int = DEFAULT_FILE_FLAGS,
            lastModified: Long = 0L,
            nullDisplayName: Boolean = false,
            openable: Boolean = true,
        ): Node {
            require(openable || content == null) { "An unopenable document can't hold content" }
            return put(
                segments = pathSegments(path),
                mimeType = mimeType,
                flags = flags,
                lastModified = lastModified,
                size = size,
                content = content,
                nullDisplayName = nullDisplayName,
                openable = openable,
            )
        }

        private fun put(
            segments: List<String>,
            mimeType: String,
            flags: Int,
            lastModified: Long,
            size: Long?,
            content: String?,
            nullDisplayName: Boolean,
            openable: Boolean,
        ): Node {
            require(segments.isNotEmpty()) { "Can't replace the root node" }
            segments.dropLast(1).indices.forEach { index ->
                val ancestor = segments.take(index + 1)
                val ancestorId = fixtureId(ancestor)
                if (!nodes.containsKey(ancestorId)) {
                    nodes[ancestorId] = Node(
                        documentId = ancestorId,
                        parentId = fixtureId(ancestor.dropLast(1)),
                        displayName = ancestor.last(),
                        mimeType = Document.MIME_TYPE_DIR,
                        flags = DEFAULT_DIR_FLAGS,
                    )
                }
            }
            val documentId = fixtureId(segments)
            val node = Node(
                documentId = documentId,
                parentId = fixtureId(segments.dropLast(1)),
                displayName = if (nullDisplayName) null else segments.last(),
                mimeType = mimeType,
                size = size,
                lastModified = lastModified,
                flags = flags,
                file = if (openable) newBackingFile(content.orEmpty()) else null,
            )
            nodes[documentId] = node
            return node
        }
    }

    companion object {
        const val ROOT_ID = "root:"

        private const val OPAQUE_ID_PREFIX = "doc-"

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

        fun tree(
            idMode: IdMode = IdMode.PATH_DERIVED,
            opaqueFixtureIds: Boolean = false,
            block: FakeDocumentsProvider.Builder.() -> Unit = {},
        ): FakeDocumentsProvider = FakeDocumentsProvider(idMode, opaqueFixtureIds).also { it.Builder().block() }

        fun pathSegments(path: String): List<String> = path.split('/').filter { it.isNotEmpty() }

        fun segmentsOf(documentId: String): List<String> =
            documentId.substringAfter(':').split('/').filter { it.isNotEmpty() }

        fun docIdOf(segments: List<String>): String = ROOT_ID + segments.joinToString("/")

        /** Opaque ids are handed out verbatim, only path based ids have a canonical form. */
        fun normalize(documentId: String): String =
            if (documentId.startsWith(OPAQUE_ID_PREFIX)) documentId else docIdOf(segmentsOf(documentId))

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
