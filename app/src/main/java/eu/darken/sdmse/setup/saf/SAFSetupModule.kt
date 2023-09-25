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
import eu.darken.sdmse.common.DeviceDetective
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.dropLastColon
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.exists
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.toLocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.files.saf.matchPermission
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.storage.PathMapper
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
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
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)
    override val state = refreshTrigger
        .mapLatest {
            State(
                paths = getAccessObjects(),
            )
        }
        .replayingShare(appScope)

    private suspend fun getAccessObjects(): List<State.PathAccess> {
        val requestObjects = mutableListOf<State.PathAccess>()

        // Android TV doesn't have the DocumentsUI app necessary to grant us permissions
        if (deviceDetective.isAndroidTV()) {
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

                    val matchedPermission = safPath.matchPermission(currentUriPerms)

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

                    State.PathAccess(
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
            storageEnvironment.externalDirs
                .map {
                    listOf(
                        it.child("Android", "data"),
                        it.child("Android", "obb"),
//                        it.child("Android", "media"),
                    )
                }
                .flatten()
                .filter { it.exists(gatewaySwitch) }
                .mapNotNull { targetPath ->
                    val safPath = pathMapper.toSAFPath(targetPath)
                    if (safPath == null) {
                        log(TAG, WARN) { "Can't map $targetPath" }
                        return@mapNotNull null
                    }

                    val matchedPermission = safPath.matchPermission(currentUriPerms)

                    val grantIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
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

                    State.PathAccess(
                        label = R.string.data_area_public_app_data_official_label.toCaString(),
                        safPath = safPath,
                        localPath = targetPath,
                        uriPermission = matchedPermission?.permission,
                        grantIntent = grantIntent,
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

    data class State(
        val paths: List<PathAccess>,
    ) : SetupModule.State {

        override val type: SetupModule.Type
            get() = SetupModule.Type.SAF

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