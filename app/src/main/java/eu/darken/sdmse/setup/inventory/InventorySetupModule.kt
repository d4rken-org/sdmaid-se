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
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.pkgs.PkgInventoryCheck
import eu.darken.sdmse.common.pkgs.getSettingsIntent
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.transformLatest
import java.time.Instant
import javax.inject.Inject
import eu.darken.sdmse.setup.SetupBinding
import javax.inject.Singleton

@Singleton
class InventorySetupModule @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val pkgOps: PkgOps,
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)

    // transformLatest, not map: every trigger has to announce itself BEFORE the query runs. With
    // Loading only on onStart, a refresh left the previous Result on screen for the whole query, so
    // a failure box and its enabled retry button stayed visible while that retry was already running.
    override val state: Flow<SetupModule.State> = refreshTrigger
        .transformLatest<String, SetupModule.State> {
            emit(Loading())

            val settled = try {
                val requiredPermission = getRequiredPermission()

                val missingPermission = requiredPermission.filter {
                    val isGranted = it.isGranted(context)
                    log(TAG) { "${it.permissionId} isGranted=$isGranted" }
                    !isGranted
                }.toSet()

                val access = when {
                    missingPermission.isNotEmpty() -> InventoryAccess.NotChecked
                    else -> {
                        val pkgs = try {
                            pkgOps.queryPkgs(PackageManager.MATCH_ALL.toLong()).map { it.packageName }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            log(TAG, ERROR) { "Inventory probe failed: ${e.asLog()}" }
                            null
                        }
                        when (pkgs) {
                            // Probe failed (binder, SecurityException, OEM PackageManager error). Don't
                            // pretend setup is complete — tools would later throw a less actionable error.
                            null -> InventoryAccess.ProbeFailed
                            else -> {
                                val result = PkgInventoryCheck.check(pkgs, context.packageName)
                                if (result != PkgInventoryCheck.Result.Valid) {
                                    val sample = pkgs.take(PKG_LOG_SAMPLE_SIZE)
                                    log(TAG, WARN) {
                                        "Inventory check returned $result for ${pkgs.size} pkgs " +
                                                "(first $PKG_LOG_SAMPLE_SIZE: $sample)"
                                    }
                                    if (Bugs.isTrace) {
                                        log(TAG, WARN) { "Inventory full list: $pkgs" }
                                    }
                                    InventoryAccess.Incomplete
                                } else {
                                    log(TAG) { "Inventory check returned $result for ${pkgs.size} pkgs" }
                                    InventoryAccess.Valid
                                }
                            }
                        }
                    }
                }

                Result(
                    missingPermission = missingPermission,
                    access = access,
                    settingsIntent = context.packageName.toPkgId().getSettingsIntent(context),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Must settle rather than propagate. Anything thrown after the Loading above kills the
                // sharing coroutine with Loading left in replayingShare's replay slot, and since the
                // coroutine is dead no refresh can ever replace it: the card is stuck on a spinner
                // forever. Same failure mode that ShizukuSetupModule guards against. queryPkgs() has
                // its own catch, but the permission checks and getSettingsIntent() do not.
                log(TAG, ERROR) { "Inventory setup state failed: ${e.asLog()}" }
                Result(
                    missingPermission = emptySet(),
                    access = InventoryAccess.ProbeFailed,
                    settingsIntent = Intent(),
                )
            }

            emit(settled)
        }
        .replayingShare(appScope)

    private fun getRequiredPermission(): Set<Permission> = buildSet {
        if (hasApiLevel(34)) add(Permission.QUERY_ALL_PACKAGES)
        add(Permission.GET_INSTALLED_APPS)
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

    sealed interface InventoryAccess {
        /** Permissions are missing, so the list was never queried. */
        data object NotChecked : InventoryAccess

        /** The query returned a usable list. */
        data object Valid : InventoryAccess

        /** A list came back, but it is not credible (empty, missing us, missing core packages). */
        data object Incomplete : InventoryAccess

        /** The query itself failed, so nothing is known about the list. */
        data object ProbeFailed : InventoryAccess
    }

    data class Result(
        val missingPermission: Set<Permission>,
        val access: InventoryAccess,
        val settingsIntent: Intent,
    ) : SetupModule.State.Current {

        override val type: SetupModule.Type
            get() = SetupModule.Type.INVENTORY

        // A failed probe keeps setup incomplete: pretending it is done would let tools run and throw a
        // less actionable error later.
        override val isComplete: Boolean = missingPermission.isEmpty() && access is InventoryAccess.Valid

    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: InventorySetupModule): SetupModule
        @Binds @SetupBinding(SetupModule.Type.INVENTORY) abstract fun named(mod: InventorySetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "Inventory", "Module")
        const val INFO_URL = "https://github.com/d4rken-org/sdmaid-se/wiki/Setup#app-inventory"
        private const val PKG_LOG_SAMPLE_SIZE = 20
    }
}
