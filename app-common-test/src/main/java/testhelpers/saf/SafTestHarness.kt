package testhelpers.saf

import android.Manifest
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import eu.darken.sdmse.common.files.saf.SAFDocFile
import eu.darken.sdmse.common.files.saf.SAFGateway
import eu.darken.sdmse.common.files.saf.SAFPath
import kotlinx.coroutines.CoroutineScope
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import testhelpers.coroutine.TestDispatcherProvider

/**
 * Wires a [FakeDocumentsProvider] into the Robolectric environment and hands out a real [SAFGateway]
 * that talks to it.
 *
 * Registration has three parts, all of them required:
 * - the delegate gets [ProviderInfo] attached so `DocumentsProvider`'s own `UriMatcher` and tree
 *   enforcement work (which is also why the info must declare `MANAGE_DOCUMENTS`),
 * - [LegacyQueryShimProvider] is the provider actually registered with the resolver (see its KDoc),
 * - the `PackageManager` gets the provider plus a [DocumentsContract.PROVIDER_INTERFACE] intent filter,
 *   without which `DocumentsContract.isDocumentUri` returns false.
 *
 * A persistable Uri permission is taken for [grantedSegments], because that is what
 * [SAFPath.findPermission] resolves against. Pass a non-empty list to model a grant that is narrower
 * than the volume (e.g. `Android/data`).
 */
class SafTestHarness(
    appScope: CoroutineScope,
    val provider: FakeDocumentsProvider = FakeDocumentsProvider.tree(),
    val authority: String = DEFAULT_AUTHORITY,
    grantedSegments: List<String> = emptyList(),
) {

    val context: Context = RuntimeEnvironment.getApplication()
    val contentResolver: ContentResolver = context.contentResolver

    /** The normalized volume root, i.e. what [SAFPath.treeRoot] holds. */
    val treeUri: Uri = treeUriFor(emptyList())

    val gateway: SAFGateway by lazy {
        SAFGateway(
            context = context,
            contentResolver = contentResolver,
            appScope = appScope,
            dispatcherProvider = TestDispatcherProvider(),
        )
    }

    init {
        val providerInfo = ProviderInfo().apply {
            this.authority = this@SafTestHarness.authority
            packageName = context.packageName
            name = LegacyQueryShimProvider::class.java.name
            applicationInfo = context.applicationInfo
            exported = true
            grantUriPermissions = true
            readPermission = Manifest.permission.MANAGE_DOCUMENTS
            writePermission = Manifest.permission.MANAGE_DOCUMENTS
        }

        provider.attachInfo(context, providerInfo)
        LegacyQueryShimProvider.register(authority, provider)
        Robolectric.buildContentProvider(LegacyQueryShimProvider::class.java).create(providerInfo).get()

        shadowOf(context.packageManager).apply {
            addOrUpdateProvider(providerInfo)
            addIntentFilterForProvider(
                ComponentName(context.packageName, LegacyQueryShimProvider::class.java.name),
                IntentFilter(DocumentsContract.PROVIDER_INTERFACE),
            )
        }

        grant(grantedSegments)
    }

    fun grant(segments: List<String>) {
        contentResolver.takePersistableUriPermission(treeUriFor(segments), SAFGateway.RW_FLAGSINT)
    }

    /**
     * Makes the provider unreachable, i.e. every `acquire*ContentProviderClient` for [authority] hands
     * back null from here on. That models the provider process being gone, which is a different answer
     * than any cursor could give.
     */
    fun killProviderProcess() {
        ShadowContentResolver.registerProviderInternal(authority, null)
    }

    fun treeUriFor(segments: List<String>): Uri =
        "content://$authority/tree/${Uri.encode(FakeDocumentsProvider.docIdOf(segments))}".toUri()

    fun safPath(vararg segments: String): SAFPath = SAFPath.build(treeUri, *segments)

    fun docUri(vararg segments: String): Uri = DocumentsContract.buildDocumentUriUsingTree(
        treeUri,
        FakeDocumentsProvider.docIdOf(segments.toList()),
    )

    fun docFile(vararg segments: String, context: Context = this.context): SAFDocFile =
        SAFDocFile(context, contentResolver, docUri(*segments))

    /**
     * Robolectric's `Context.checkCallingOrSelfUriPermission` returns GRANTED unconditionally (it models
     * a single process), so the denied branches of [SAFDocFile.readable] / [SAFDocFile.writable] need
     * a context that says otherwise.
     */
    fun deniedUriPermissionContext(): Context = object : ContextWrapper(context) {
        override fun checkCallingOrSelfUriPermission(uri: Uri?, modeFlags: Int): Int =
            PackageManager.PERMISSION_DENIED
    }

    companion object {
        const val DEFAULT_AUTHORITY = "eu.darken.sdmse.test.documents"
    }
}
