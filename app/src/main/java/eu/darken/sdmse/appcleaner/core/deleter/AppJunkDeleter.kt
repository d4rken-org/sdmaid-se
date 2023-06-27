package eu.darken.sdmse.appcleaner.core.deleter

import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.InaccessibleDeletionException
import eu.darken.sdmse.appcleaner.core.automation.ClearCacheTask
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
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
        val inaccessibleDeletionResult: InaccessibleDeletionResult?,
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

        val confirmedTargets = targetPkgs ?: snapshot.junks.map { it.identifier }

        confirmedTargets.forEach { targetPkg ->
            log(TAG) { "Processing $targetPkg" }
            if (onlyInaccessible) return@forEach

            val appJunk = snapshot.junks.single { it.identifier == targetPkg }
            updateProgressPrimary(appJunk.label)

            val targetFilters = targetFilters
                ?: appJunk.expendables?.keys
                ?: emptySet()

            val targetFiles: Collection<APathLookup<*>> = targetContents
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

            deletionMap[appJunk.identifier] = deleted
        }

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_loading)
        updateProgressSecondary(CaString.EMPTY)

        val currentUser = userManager.currentUser()
        val inaccessibleTargets = confirmedTargets
            .filter { includeInaccessible }
            .map { targetPkg -> snapshot.junks.single { it.identifier == targetPkg } }
            .filter { junk -> junk.inaccessibleCache != null }
            .filter {
                // Without root, we shouldn't have inaccessible caches from other users
                val isCurrentUser = it.identifier.userHandle == currentUser.handle
                if (!isCurrentUser) {
                    log(TAG, WARN) { "Unexpected inaccessible data from other users: $it" }
                }
                isCurrentUser
            }
            .takeIf { it.isNotEmpty() }

        val automationResult: InaccessibleDeletionResult? = when {
            inaccessibleTargets == null -> {
                log(TAG) { "No inaccessible caches to delete." }
                null
            }

            shizukuManager.canUseShizukuNow() && targetPkgs == null -> {
                log(TAG) { "Using Shizuku to delete inaccessible caches" }
                updateProgressPrimary(R.string.appcleaner_progress_shizuku_deleting_caches)
                updateProgressCount(Progress.Count.Indeterminate())

                val success = inaccessibleTargets
                    .filter { !it.pkg.isSystemApp }
                    .map { it.identifier }

                var failed = inaccessibleTargets
                    .filter { !it.pkg.isSystemApp }
                    .map { it.identifier }

                try {
                    pkgOps.trimCaches(Long.MAX_VALUE)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Trimming caches failed: ${e.asLog()}" }
                    failed = failed + success
                }

                ShizukuDeletionResult(successful = success, failed = failed)
            }

            else -> {
                log(TAG, WARN) { "Using accessibility service to delete inaccesible caches." }
                updateProgressPrimary(R.string.appcleaner_automation_loading)
                updateProgressSecondary(CaString.EMPTY)

                try {
                    automationManager.submit(
                        ClearCacheTask(inaccessibleTargets.map { it.identifier })
                    ) as InaccessibleDeletionResult
                } catch (e: AutomationUnavailableException) {
                    throw InaccessibleDeletionException(e)
                }
            }
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
                        automationResult?.successful?.contains(appJunk.identifier) == true -> null
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
            automationResult
        )
    }

    companion object {
        private val TAG = logTag("AppCleaner", "Deleter")
    }
}
