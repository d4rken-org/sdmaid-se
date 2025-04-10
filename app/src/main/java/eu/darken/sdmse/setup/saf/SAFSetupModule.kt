package eu.darken.sdmse.setup.saf

import android.content.ContentResolver
import android.content.Intent
import android.content.UriPermission
import android.net.Uri
import android.provider.DocumentsContract
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.dropLastColon
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.exists
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.toLocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.files.saf.findPermission
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.storage.PathMapper
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SAFSetupModule @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val contentResolver: ContentResolver,
    private val storageManager2: StorageManager2,
    private val storageEnvironment: StorageEnvironment,
    private val pathMapper: PathMapper,
    private val dataAreaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val deviceDetective: DeviceDetective,
    private val pkgOps: PkgOps,
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)
    override val state: Flow<SetupModule.State> = refreshTrigger
        .mapLatest {
            @Suppress("USELESS_CAST")
            Result(
                paths = getAccessObjects(),
            ) as SetupModule.State
        }
        .onStart { emit(Loading()) }
        .replayingShare(appScope)

    private suspend fun getAccessObjects(): List<Result.PathAccess> {
        val requestObjects = mutableListOf<Result.PathAccess>()

        // Android TV doesn't have the DocumentsUI app necessary to grant us permissions
        if (deviceDetective.getROMType() == RomType.ANDROID_TV) {
            log(TAG) { "Skipping SAF setup as this is an Android TV device." }
            return requestObjects
        }

        val currentUriPerms = contentResolver.persistedUriPermissions

        if (!hasApiLevel(30)) {
            storageManager2.storageVolumes
                .filter {
                    if (it.directory == null) {
                        log(TAG, INFO) { "Storage not backed by a path: $it" }
                        return@filter false
                    }
                    if (!it.isMounted) {
                        log(TAG, WARN) { "Storage not mounted: $it" }
                        return@filter false
                    }
                    return@filter true
                }
                .mapNotNull { volume ->
                    val targetPath = volume.directory!!.toLocalPath()
                    val safPath = pathMapper.toSAFPath(targetPath)
                    if (safPath == null) {
                        log(TAG, WARN) { "Can't map $targetPath" }
                        return@mapNotNull null
                    }

                    val matchedPermission = safPath.findPermission(currentUriPerms)

                    val label = when (volume.isRemovable) {
                        true -> R.string.data_area_sdcard_label.toCaString()
                        else -> R.string.data_area_public_storage_label.toCaString()
                    }

                    var grantIntent = if (!hasApiLevel(29)) {
                        volume.createAccessIntent()
                    } else {
                        null
                    }

                    if (grantIntent == null) {
                        grantIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                            putExtra("android.content.extra.SHOW_ADVANCED", true)
                            val navigationUri = safPath.pathUri.buildUpon().apply {
                                path("")
                                appendPath("document")
                                safPath.pathUri.pathSegments.drop(1).forEach {
                                    appendPath(it)
                                }
                            }.build()
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, navigationUri)
                        }
                    }

                    Result.PathAccess(
                        label = label,
                        safPath = safPath,
                        localPath = targetPath,
                        uriPermission = matchedPermission?.permission,
                        grantIntent = grantIntent,
                    ).run { requestObjects.add(this) }
                }
        }

        /**
         * This uses a trick in the SAF path picker on Android 11.
         * If you tell it to open on `Android/data` it will still show the folder+content, and you can select it.
         * And in contrast to selecting `Android/` which does not give access to `Android/data`, selecting `Android/data` directly works.
         * On Android 13 this trick no longer works :(
         */
        if (hasApiLevel(30) && !hasApiLevel(33)) {
            val documentsPkg = pkgOps.queryAppInfos("com.google.android.documentsui".toPkgId())
            log(TAG) { "Files-DocumentsUI: $documentsPkg targetSdkVersion=${documentsPkg?.targetSdkVersion}" }

            storageEnvironment.externalDirs
                .map { baseDir ->
                    val viableTargets = mutableListOf<LocalPath>()

                    // The newer `Files` app if updates through Google Play system updates, no longer supports selecting this
                    // https://cs.android.com/android/platform/superproject/main/+/main:packages/apps/DocumentsUI/src/com/android/documentsui/picker/ActionHandler.java;l=84;bpv=1;bpt=0;drc=901f1d6044aade190bb943ccc18d26244132648e;dlc=306a2b606a1f01498d2d83a1d8362962f114e6e8
                    if ((documentsPkg?.targetSdkVersion ?: 0) < 34) {
                        viableTargets.add(baseDir.child("Android", "data"))
                        viableTargets.add(baseDir.child("Android", "obb"))
                    }

                    // We don't need extra permission for this AFAIK
                    // viableTargets.add(baseDir.child("Android", "media"))

                    viableTargets
                }
                .flatten()
                .filter { it.exists(gatewaySwitch) }
                .mapNotNull { targetPath ->
                    val safPath = pathMapper.toSAFPath(targetPath)
                    if (safPath == null) {
                        log(TAG, WARN) { "Can't map $targetPath" }
                        return@mapNotNull null
                    }

                    val matchedPermission = safPath.findPermission(currentUriPerms)

                    val grantIntentSDMOg = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        putExtra("android.content.extra.SHOW_ADVANCED", true)

                        // Works on Android 12, but not some devices with newer security patches
                        // content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fdata/document/primary%3AAndroid%2Fdata
                        val navTreeUri = DocumentsContract.buildDocumentUriUsingTree(
                            safPath.pathUri,
                            DocumentsContract.getTreeDocumentId(safPath.pathUri)
                        )
                        log(TAG) { "NAV-TREE-URI: $navTreeUri" }

                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, navTreeUri)
                    }

                    Result.PathAccess(
                        label = when (safPath.name) {
                            "obb" -> R.string.data_area_public_app_assets_official_label.toCaString()
                            else -> R.string.data_area_public_app_data_official_label.toCaString()
                        },
                        safPath = safPath,
                        localPath = targetPath,
                        uriPermission = matchedPermission?.permission,
                        grantIntent = grantIntentSDMOg,
                    ).run { requestObjects.add(this) }
                }
        }

        // TODO provide some more elaborate lookups for TV boxes that struggle with this?
        // See https://commonsware.com/blog/2017/12/27/storage-access-framework-missing-action.html

        log(TAG) { "Generated: $requestObjects" }
        return requestObjects
    }

    suspend fun takePermission(uri: Uri) {
        log(TAG) { "takePermission(uri=$uri)" }
        val match = getAccessObjects().singleOrNull {
            log(TAG, VERBOSE) { "Comparing $uri with ${it.safPath.pathUri}" }
            it.safPath.pathUri == uri.dropLastColon()
        }
        if (match == null) {
            log(TAG, WARN) { "We don't need acces to $uri" }
            throw IllegalArgumentException("Wrong path")
        }

        pathMapper.takePermission(uri)
        dataAreaManager.reload()
        refresh()
    }

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = rngString
    }

    data class Loading(
        override val startAt: Instant = Instant.now(),
    ) : SetupModule.State.Loading {
        override val type: SetupModule.Type = SetupModule.Type.SAF
    }

    data class Result(
        val paths: List<PathAccess>,
    ) : SetupModule.State.Current {

        override val type: SetupModule.Type = SetupModule.Type.SAF

        override val isComplete: Boolean = paths.all { it.hasAccess }

        data class PathAccess(
            val label: CaString,
            val safPath: SAFPath,
            val localPath: LocalPath,
            val uriPermission: UriPermission?,
            val grantIntent: Intent,
        ) {
            val hasAccess: Boolean = uriPermission != null
        }
    }


    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: SAFSetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "SAF", "Module")
    }
}