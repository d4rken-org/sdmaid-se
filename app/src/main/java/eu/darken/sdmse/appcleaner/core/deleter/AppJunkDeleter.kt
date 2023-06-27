package eu.darken.sdmse.appcleaner.core.deleter

import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.InaccessibleDeletionException
import eu.darken.sdmse.appcleaner.core.automation.ClearCacheTask
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.automation.core.AutomationManager
import eu.darken.sdmse.automation.core.errors.AutomationUnavailableException
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.WriteException
import eu.darken.sdmse.common.files.deleteAll
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.matches
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.common.shizuku.canUseShizukuNow
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlin.reflect.KClass


class AppJunkDeleter @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
    private val userManager: UserManager2,
    private val automationManager: AutomationManager,
    private val shizukuManager: ShizukuManager,
    private val pkgOps: PkgOps,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.DEFAULT_STATE.copy(
            primary = eu.darken.sdmse.common.R.string.general_progress_preparing.toCaString()
        )
    )
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun initialize() {
        log(TAG, VERBOSE) { "initialize()" }
    }

    data class Result(
        val newSnapShot: AppCleaner.Data,
        val deletionMap: Map<Installed.InstallId, Set<APathLookup<*>>>,
        val inaccessibleDeletions: Collection<InaccessibleCache>,
    )

    suspend fun delete(
        snapshot: AppCleaner.Data,
        targetPkgs: Set<Installed.InstallId>? = null,
        targetFilters: Set<KClass<out ExpendablesFilter>>? = null,
        targetContents: Set<APath>? = null,
        includeInaccessible: Boolean = true,
        onlyInaccessible: Boolean = false,
    ): Result {
        log(TAG, INFO) { "delete()..." }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_preparing)
        updateProgressCount(Progress.Count.Indeterminate())

        val deletionMap = mutableMapOf<Installed.InstallId, Set<APathLookup<*>>>()

        val targetJunk = targetPkgs
            ?.map { tp -> snapshot.junks.single { it.identifier == tp } }
            ?: snapshot.junks

        if (!onlyInaccessible) {
            targetJunk.forEach { appJunk ->
                val deleted = deleteDirectAccess(appJunk, targetFilters, targetContents)
                deletionMap[appJunk.identifier] = deleted
            }
        }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)
        updateProgressSecondary(CaString.EMPTY)

        val successTargets = if (includeInaccessible) {
            deleteInaccessible(targetJunk, isAllApps = targetPkgs == null)
        } else {
            emptySet()
        }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_filtering)
        updateProgressSecondary(CaString.EMPTY)

        val newSnapshot = snapshot.copy(
            junks = snapshot.junks
                .map { appJunk ->
                    updateProgressSecondary(appJunk.label)
                    // Remove all files we deleted or children of deleted files
                    val updatedExpendables = appJunk.expendables
                        ?.mapValues { (type, typeFiles) ->
                            typeFiles.filter { file ->
                                val mapContent = deletionMap[appJunk.identifier]
                                mapContent?.none { it.matches(file) || it.isAncestorOf(file) } ?: true
                            }
                        }
                        ?.filterValues { it.isNotEmpty() }

                    val updatedInaccesible = when {
                        successTargets.contains(appJunk.inaccessibleCache) -> null
                        else -> appJunk.inaccessibleCache
                    }

                    appJunk.copy(
                        expendables = updatedExpendables,
                        inaccessibleCache = updatedInaccesible,
                    )
                }
                .filter { !it.isEmpty() }
        )

        return Result(
            newSnapshot,
            deletionMap,
            successTargets
        )
    }

    private suspend fun deleteDirectAccess(
        appJunk: AppJunk,
        _targetFilters: Set<KClass<out ExpendablesFilter>>?,
        _targetContents: Set<APath>?,
    ): Set<APathLookup<*>> {
        log(TAG) { "Processing ${appJunk.identifier}" }

        updateProgressPrimary(appJunk.label)

        val targetFilters = _targetFilters
            ?: appJunk.expendables?.keys
            ?: emptySet()

        val targetFiles: Collection<APathLookup<*>> = _targetContents
            ?.map { tc ->
                val allFiles = appJunk.expendables?.values?.flatten() ?: emptySet()
                allFiles.single { tc.matches(it) }
            }
            ?: appJunk.expendables?.filterKeys { targetFilters.contains(it) }?.values?.flatten()
            ?: emptySet()

        val deleted = mutableSetOf<APathLookup<*>>()

        targetFiles.forEach { targetFile ->
            log(TAG) { "Deleting $targetFile..." }
            try {
                targetFile.deleteAll(gatewaySwitch) {
                    updateProgressSecondary(it.userReadablePath)
                    true
                }
                log(TAG) { "Deleted $targetFile!" }
                deleted.add(targetFile)
            } catch (e: WriteException) {
                log(TAG, WARN) { "Deletion failed for $targetFile" }
            }
        }

        return deleted
    }

    private suspend fun deleteInaccessible(
        targetJunks: Collection<AppJunk>,
        isAllApps: Boolean,
    ): Set<InaccessibleCache> {
        val currentUser = userManager.currentUser()
        val inaccessibleTargets = targetJunks
            .filter { junk -> junk.inaccessibleCache != null }
            .filter {
                // Without root, we shouldn't have inaccessible caches from other users
                val isCurrentUser = it.identifier.userHandle == currentUser.handle
                if (!isCurrentUser) {
                    log(TAG, WARN) { "Unexpected inaccessible data from other users: $it" }
                }
                isCurrentUser
            }

        log(TAG) { "${inaccessibleTargets.size} inaccessible caches to delete." }


        val successTargets = mutableListOf<InaccessibleCache>()
        val failedTargets = mutableListOf<InaccessibleCache>()

        if (shizukuManager.canUseShizukuNow() && inaccessibleTargets.isNotEmpty() && isAllApps) {
            log(TAG) { "Using Shizuku to delete inaccessible caches" }
            updateProgressPrimary(R.string.appcleaner_progress_shizuku_deleting_caches)
            updateProgressCount(Progress.Count.Indeterminate())

            val trimCandidates = inaccessibleTargets.filter { !it.pkg.isSystemApp }

            try {
                pkgOps.trimCaches(Long.MAX_VALUE)
                successTargets.addAll(trimCandidates.map { it.inaccessibleCache!! })
            } catch (e: Exception) {
                log(TAG, ERROR) { "Trimming caches failed: ${e.asLog()}" }
                failedTargets.addAll(trimCandidates.map { it.inaccessibleCache!! })
            }
        }


        log(TAG, WARN) { "Using accessibility service to delete inaccesible caches." }
        updateProgressPrimary(R.string.appcleaner_automation_loading)
        updateProgressSecondary(CaString.EMPTY)

        if (inaccessibleTargets.isNotEmpty() && inaccessibleTargets.size != successTargets.size) {
            val remainingTargets = inaccessibleTargets
                .filter { !successTargets.contains(it.inaccessibleCache) }
                .mapNotNull { it.inaccessibleCache }

            log(TAG) { "Processing ${remainingTargets.size} remaining inaccessible caches" }
            val acsTask = ClearCacheTask(remainingTargets.map { it.identifier })
            val result = try {
                automationManager.submit(acsTask) as ClearCacheTask.Result
            } catch (e: AutomationUnavailableException) {
                throw InaccessibleDeletionException(e)
            }
            successTargets.addAll(remainingTargets.filter { result.successful.contains(it.identifier) })
        }

        return successTargets.toSet()
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Deleter")
    }
}
