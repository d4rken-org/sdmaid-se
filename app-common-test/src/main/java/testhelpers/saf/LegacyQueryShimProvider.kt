package eu.darken.sdmse.common.files.saf

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract

/**
 * Restores production-equivalent query dispatch for a [FakeDocumentsProvider] running under Robolectric.
 *
 * This patches a **Robolectric fidelity gap, not a production defect** - do not "fix" [SAFDocFile] to
 * match this shim.
 *
 * On a device, `ContentResolver.query(uri, projection, selection, selectionArgs, sortOrder)` packs the
 * SQL arguments into a [Bundle] and dispatches over Binder to the provider's
 * `query(Uri, String[], Bundle, CancellationSignal)` overload, which is the one `DocumentsProvider`
 * implements. Robolectric's `ShadowContentResolver` skips that packing and calls the provider's 5-arg
 * overload directly. In `DocumentsProvider` that overload is `final` and throws
 * `UnsupportedOperationException: Pre-Android-O query format not supported.`, so every
 * [SAFDocFile.queryForString] / [SAFDocFile.queryForLong] call would fail for a reason that cannot
 * happen in production. `androidx.documentfile.DocumentFile` uses the very same call shape.
 *
 * So this provider is what gets registered with the resolver: it repacks the SQL arguments the way the
 * platform does and forwards to the [FakeDocumentsProvider] delegate. Everything else is passed straight
 * through, including the calls that are expected to fail (e.g. `update`, which `DocumentsProvider`
 * implements as final + throwing).
 */
class LegacyQueryShimProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    @Suppress("DEPRECATION")
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        // ContentResolver.createSqlQueryBundle() is not public API, so the bundle is built from the
        // three public QUERY_ARG_SQL_* constants the platform uses for exactly this.
        val queryArgs = Bundle().apply {
            selection?.let { putString(ContentResolver.QUERY_ARG_SQL_SELECTION, it) }
            selectionArgs?.let { args -> putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toList().toTypedArray()) }
            sortOrder?.let { putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, it) }
        }
        return delegate(uri.authority).query(uri, projection, queryArgs, null)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?,
    ): Cursor? = delegate(uri.authority).query(uri, projection, queryArgs, cancellationSignal)

    override fun getType(uri: Uri): String? = delegate(uri.authority).getType(uri)

    override fun insert(uri: Uri, values: ContentValues?): Uri? = delegate(uri.authority).insert(uri, values)

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = delegate(uri.authority).update(uri, values, selection, selectionArgs)

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        delegate(uri.authority).delete(uri, selection, selectionArgs)

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? =
        delegate(uri.authority).openFile(uri, mode)

    override fun openFile(uri: Uri, mode: String, signal: CancellationSignal?): ParcelFileDescriptor? =
        delegate(uri.authority).openFile(uri, mode, signal)

    @Suppress("DEPRECATION")
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val target = extras?.getParcelable<Uri>(EXTRA_URI)
        return delegate(target?.authority).call(method, arg, extras)
    }

    override fun call(authority: String, method: String, arg: String?, extras: Bundle?): Bundle? =
        delegate(authority).call(method, arg, extras)

    private fun delegate(authority: String?): FakeDocumentsProvider = requireNotNull(
        delegates[authority] ?: delegates.values.singleOrNull()
    ) { "No FakeDocumentsProvider registered for authority=$authority" }

    companion object {
        /** `DocumentsContract.EXTRA_URI`, which is hidden API. */
        private const val EXTRA_URI = "uri"

        private val delegates = mutableMapOf<String, FakeDocumentsProvider>()

        fun register(authority: String, delegate: FakeDocumentsProvider) {
            delegates[authority] = delegate
        }

        fun unregisterAll() {
            delegates.clear()
        }
    }
}
