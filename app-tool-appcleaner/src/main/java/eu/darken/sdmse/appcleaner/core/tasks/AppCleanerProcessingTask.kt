package eu.darken.sdmse.appcleaner.core.tasks

import eu.darken.sdmse.appcleaner.R
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.serialization.KClassParcelizer
import eu.darken.sdmse.stats.core.ReportDetails
import eu.darken.sdmse.stats.core.Reportable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlin.reflect.KClass

@Parcelize
@TypeParceler<KClass<out ExpendablesFilter>, KClassParcelizer>()
data class AppCleanerProcessingTask(
    val targetPkgs: Set<InstallId>? = null,
    val targetFilters: Set<KClass<out ExpendablesFilter>>? = null,
    val targetContents: Set<APath>? = null,
    val includeInaccessible: Boolean = true,
    val onlyInaccessible: Boolean = false,
    val useAutomation: Boolean = true,
    val isBackground: Boolean = false,
) : AppCleanerTask, Reportable {

    init {
        // `targetContents` without `targetPkgs` would smear the path-list across every junk in the
        // snapshot: AppCleaner.performProcessing iterates each junk's `allMatches` and tries to
        // `single { tc.matches(it.path) }` against every targetContent, which throws as soon as a
        // path is missing from any junk. No current caller produces this shape — the contract
        // ensures future callers can't reintroduce it.
        require(targetContents == null || targetPkgs != null) {
            "targetContents requires targetPkgs to bound which junk owns each path"
        }
    }


    sealed interface Result : AppCleanerTask.Result

    @Parcelize
    data class Success(
        override val affectedSpace: Long,
        override val affectedPaths: Set<APath>,
        // Explicit item count: `affectedPaths` only holds the paths we can enumerate. Caches cleared via the
        // accessibility-service "Clear cache" tap contribute just their synthetic dir paths, so the path count
        // would massively undershoot what the scan reported as "found". This carries the scan-scale count instead.
        override val affectedCount: Int = affectedPaths.size,
        // Set when the clean aborted after deleting this much, e.g. the accessibility stage hit a
        // locked screen. Null for a run that went through everything it targeted.
        val stoppedEarly: AppCleanerTask.StopReason? = null,
        val skippedCount: Int = 0,
    ) : Result, ReportDetails.AffectedSpace, ReportDetails.AffectedPaths {

        override val primaryInfo
            get() = caString {
                when (stoppedEarly) {
                    AppCleanerTask.StopReason.SCREEN_UNAVAILABLE -> getQuantityString2(
                        R.plurals.appcleaner_result_x_items_deleted_stopped_screen,
                        affectedCount,
                    )

                    AppCleanerTask.StopReason.ERROR -> getQuantityString2(
                        R.plurals.appcleaner_result_x_items_deleted_stopped_error,
                        affectedCount,
                    )

                    AppCleanerTask.StopReason.AUTOMATION_NO_CONSENT -> getQuantityString2(
                        R.plurals.appcleaner_result_x_items_deleted_stopped_automation,
                        skippedCount,
                        getQuantityString2(R.plurals.appcleaner_result_x_items_deleted, affectedCount),
                        skippedCount,
                    )

                    null -> getQuantityString2(R.plurals.appcleaner_result_x_items_deleted, affectedCount)
                }
            }

        override val secondaryInfo
            get() = caString {
                val (formatted, quantity) = ByteFormatter.formatSize(this, affectedSpace)
                getQuantityString2(
                    eu.darken.sdmse.common.R.plurals.general_result_x_space_freed,
                    quantity,
                    formatted,
                )
            }

        override fun toString(): String {
            return "AppCleanerProcessingTask.Success($affectedSpace,$affectedCount items,${affectedPaths.size} paths)"
        }
    }
}