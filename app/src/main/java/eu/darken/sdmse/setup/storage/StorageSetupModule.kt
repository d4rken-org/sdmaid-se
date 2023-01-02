package eu.darken.sdmse.setup.storage

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject

@Reusable
class StorageSetupModule @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager2: StorageManager2,
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)
    override val state = refreshTrigger.mapLatest {
        if (!hasApiLevel(30)) {
            log(TAG) { "<API30, MANAGE_EXTERNAL_STORAGE is not required." }
            return@mapLatest null
        }

        val requiredPermission = getRequiredPermission()
        val missingPermission = requiredPermission.filter {
            val isGranted = it.isGranted(context)
            log(TAG) { "${it.permissionId} isGranted=$isGranted" }
            !isGranted
        }.toSet()

        val affectedPaths = storageManager2.storageVolumes
            .filter { it.directory != null }
            .map { volume ->
                State.PathAccess(
                    label = when {
                        volume.isPrimary || volume.isEmulated -> R.string.data_area_public_storage_label.toCaString()
                        else -> R.string.data_area_sdcard_label.toCaString()
                    },
                    localPath = LocalPath.build(volume.directory!!),
                    hasAccess = requiredPermission.all { it.isGranted(context) },
                )
            }

        return@mapLatest State(
            missingPermission = missingPermission,
            paths = affectedPaths,
        )
    }

    private fun getRequiredPermission(): Set<Permission> {
        return when {
            hasApiLevel(30) -> setOf(Permission.MANAGE_EXTERNAL_STORAGE)
            else -> setOf(
                Permission.WRITE_EXTERNAL_STORAGE,
                Permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = rngString
    }

    data class State(
        val paths: List<PathAccess>,
        val missingPermission: Set<Permission>,
    ) : SetupModule.State {

        override val isComplete: Boolean = missingPermission.isEmpty() && paths.all { it.hasAccess }

        data class PathAccess(
            val label: CaString,
            val localPath: LocalPath,
            val hasAccess: Boolean,
        )
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: StorageSetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "Storage", "Module")
    }
}