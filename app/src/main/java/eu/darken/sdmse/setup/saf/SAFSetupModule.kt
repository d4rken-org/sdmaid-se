package eu.darken.sdmse.setup.saf

import android.content.ContentResolver
import android.content.Intent
import android.content.UriPermission
import android.net.Uri
import android.provider.DocumentsContract
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.dropLastColon
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.exists
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.local.toLocalPath
import eu.darken.sdmse.common.files.core.saf.SAFPath
import eu.darken.sdmse.common.files.core.saf.matchPermission
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.storage.SAFMapper
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject

@Reusable
class SAFSetupModule @Inject constructor(
    private val contentResolver: ContentResolver,
    private val storageManager2: StorageManager2,
    private val storageEnvironment: StorageEnvironment,
    private val safMapper: SAFMapper,
    private val dataAreaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)
    override val state = refreshTrigger.mapLatest {
        State(
            paths = getAccessObjects(),
        )
    }

    private suspend fun getAccessObjects(): List<State.PathAccess> {
        val requestObjects = mutableListOf<State.PathAccess>()
        val currentUriPerms = contentResolver.persistedUriPermissions

        if (!hasApiLevel(30)) {
            storageManager2.storageVolumes
                .filter { it.directory != null }
                .mapNotNull { volume ->
                    val targetPath = volume.directory!!.toLocalPath()
                    val safPath = safMapper.toSAFPath(targetPath)
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
                    val safPath = safMapper.toSAFPath(targetPath)
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

        safMapper.takePermission(uri)
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