package eu.darken.sdmse.setup.inventory

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.pkgs.getSettingsIntent
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.rngString
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
class InventorySetupModule @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val pkgOps: PkgOps,
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)
    override val state: Flow<SetupModule.State> = refreshTrigger
        .mapLatest {
            val requiredPermission = getRequiredPermission()

            val missingPermission = requiredPermission.filter {
                val isGranted = it.isGranted(context)
                log(TAG) { "${it.permissionId} isGranted=$isGranted" }
                !isGranted
            }.toSet()

            val isAccessFaked = when {
                missingPermission.isEmpty() -> {
                    run {
                        val pkgs = try {
                            pkgOps.queryPkgs(PackageManager.MATCH_ALL.toLong()).map { it.packageName }
                        } catch (e: Exception) {
                            log(TAG, ERROR) { "Check for fake access failed: ${e.asLog()}" }
                            null
                        }
                        when {
                            pkgs == null -> false
                            pkgs.isEmpty() -> true
                            else -> !pkgs.contains(context.packageName)
                        }
                    }
                }

                else -> false
            }

            @Suppress("USELESS_CAST")
            Result(
                missingPermission = missingPermission,
                isAccessFaked = isAccessFaked,
                settingsIntent = context.packageName.toPkgId().getSettingsIntent(context)
            ) as SetupModule.State
        }
        .onStart { emit(Loading()) }
        .replayingShare(appScope)

    private fun getRequiredPermission(): Set<Permission> = when {
        hasApiLevel(34) -> setOf(Permission.QUERY_ALL_PACKAGES)
        else -> emptySet()
    }

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = rngString
    }

    data class Loading(
        override val startAt: Instant = Instant.now(),
    ) : SetupModule.State.Loading {
        override val type: SetupModule.Type = SetupModule.Type.INVENTORY
    }

    data class Result(
        val missingPermission: Set<Permission>,
        val isAccessFaked: Boolean,
        val settingsIntent: Intent,
    ) : SetupModule.State.Current {

        override val type: SetupModule.Type
            get() = SetupModule.Type.INVENTORY

        override val isComplete: Boolean = !isAccessFaked && missingPermission.isEmpty()

    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: InventorySetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "Inventory", "Module")
        const val INFO_URL = "https://github.com/d4rken-org/sdmaid-se/wiki/Setup#app-inventory"
    }
}