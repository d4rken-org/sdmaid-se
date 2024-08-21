package eu.darken.sdmse.appcleaner.core.scanner

import dagger.Reusable
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.excludeNestedLookups
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilterIdentifier
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPrivateFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPublicFilter
import eu.darken.sdmse.appcleaner.core.identifier
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.containsSegments
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.common.shizuku.canUseShizukuNow
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.pathExclusions
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@Reusable
class PostProcessorModule @Inject constructor(
    private val rootManager: RootManager,
    private val shizukuManager: ShizukuManager,
    private val exclusionManager: ExclusionManager,
    private val settings: AppCleanerSettings,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    suspend fun postProcess(apps: Collection<AppJunk>): Collection<AppJunk> {
        log(TAG) { "postProcess(${apps.size})" }

        val minCacheSize = settings.minCacheSizeBytes.value()
        log(TAG, INFO) { "Minimum cache size is $minCacheSize" }

        val processed = apps
            .map { checkAliasedItems(it) }
            .mapNotNull { checkExclusions(it) }
            .map { checkForHiddenModules(it) }
            .filter {
                val isMinSize = it.size >= minCacheSize
                if (!isMinSize) log(TAG) { "Below minimum size: $it" }
                isMinSize
            }
            .filter { !it.isEmpty() }

        log(TAG) { "After post processing: ${apps.size} reduced to ${processed.size}" }
        return processed
    }

    // TODO can we replace this by just working with sets in previous steps?
    private fun checkAliasedItems(before: AppJunk): AppJunk {
        if (before.expendables.isNullOrEmpty()) return before

        val after = before.copy(
            expendables = before.expendables
                .mapValues { (key, value) ->
                    value.distinctBy { it.path }
                }
                .filter { it.value.isNotEmpty() }
        )

        if (before.expendables != after.expendables!!) {
            log(TAG) { "Before duplicate/aliased check: ${before.expendables.size}" }
            val beforeAll = before.expendables.map { it.value }.flatten()
            val afterAll = after.expendables.map { it.value }.flatten()
            log(TAG, WARN) { "Overlapping items: ${beforeAll.subtract(afterAll.toSet())}" }
        }

        return after
    }

    private suspend fun checkExclusions(before: AppJunk): AppJunk? {
        // Empty apps don't generate edge cases (and are omitted).
        if (before.expendables.isNullOrEmpty()) return before

        val useShizuku = shizukuManager.canUseShizukuNow()

        if (useShizuku && before.pkg.id == shizukuManager.pkgId) {
            log(TAG, WARN) { "Shizuku is being used, excluding it." }
            return null
        }

        val useRoot = rootManager.canUseRootNow()

        val edgeCaseMap = mutableMapOf<ExpendablesFilterIdentifier, Collection<ExpendablesFilter.Match>>()

        if (!useRoot && useShizuku) {
            val edgeCaseSegs = segs(before.pkg.id.name, "cache")
            val edgeCaseFilters = setOf(
                DefaultCachesPublicFilter::class.identifier,
                DefaultCachesPrivateFilter::class.identifier,
            )
            before.expendables
                .filter { edgeCaseFilters.contains(it.key) }
                .forEach { (type, matches) ->
                    val edgeCases = matches.filter { it.path.segments.containsSegments(edgeCaseSegs) }
                    edgeCaseMap[type] = edgeCases
                }
        }

        val exclusions = exclusionManager.pathExclusions(SDMTool.Type.APPCLEANER)

        var after = before.copy(
            expendables = before.expendables.mapValues { (type, paths) ->
                exclusions.excludeNestedLookups(paths)
            }
        )

        after = after.copy(
            expendables = after.expendables?.mapValues { (type, paths) ->
                val edges = edgeCaseMap[type] ?: emptySet()
                if (edges.isNotEmpty()) log(TAG, VERBOSE) { "Re-adding edge cases: $edges" }
                (paths + edges).toSet()
            }
        )

        if (before.itemCount != after.itemCount) {
            log(TAG) { "Before checking exclusions: $before" }
            log(TAG) { "After checking exclusions: $after" }
        }

        if (after.itemCount > before.itemCount) {
            throw IllegalStateException("Item count after exclusions can't be greater than before!")
        }

        return after
    }

    // https://github.com/d4rken/sdmaid-public/issues/2659
    private fun checkForHiddenModules(before: AppJunk): AppJunk {
        if (!hasApiLevel(29)) return before

        return if (HIDDEN_Q_PKGS.contains(before.pkg.id)) {
            before.copy(
                inaccessibleCache = null
            )
        } else {
            before
        }
    }

    companion object {
        val HIDDEN_Q_PKGS = listOf(
            "com.google.android.networkstack.permissionconfig",
            "com.google.android.ext.services",
            "com.google.android.angle",
            "com.google.android.documentsui",
            "com.google.android.modulemetadata",
            "com.google.android.networkstack",
            "com.google.android.permissioncontroller",
            "com.google.android.captiveportallogin"
        ).map { it.toPkgId() }
        val TAG = logTag("AppCleaner", "Scanner", "PostProcessor")
    }

}