package eu.darken.sdmse.systemcleaner.core.filter.custom

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.identifyArea
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import eu.darken.sdmse.systemcleaner.ui.customfilter.editor.CustomFilterEditorOptions
import javax.inject.Inject

@Reusable
class EditorOptionsCreator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileForensics: FileForensics,
) {

    suspend fun createOptions(targets: Set<APathLookup<*>>): CustomFilterEditorOptions {
        log(TAG, VERBOSE) { "Determining options for ${targets.size} targets:\n${targets.joinToString("\n")}" }

        val areaInfos = targets.mapNotNull { target ->
            fileForensics.identifyArea(target).also {
                if (it == null) log(TAG, WARN) { "Failed to determine AreaInfo for $target" }
            }
        }

        val label = areaInfos
            .map { it.prefix }
            .toSet().singleOrNull()
            ?.let {
                val pathPrefix = it.userReadablePath.get(context)
                val itemCount = context.getQuantityString2(
                    eu.darken.sdmse.common.R.plurals.result_x_items,
                    targets.size
                )
                "$pathPrefix - $itemCount"
            }

        val targetAreas = areaInfos.map { it.dataArea.type }.toSet()

        val pathCriteria = targets.map {
            when (it.fileType) {
                FileType.DIRECTORY -> SegmentCriterium(
                    it.segments + segs(""),
                    mode = SegmentCriterium.Mode.Start(allowPartial = true),
                )

                FileType.SYMBOLIC_LINK,
                FileType.FILE,
                FileType.UNKNOWN -> SegmentCriterium(
                    it.segments,
                    mode = SegmentCriterium.Mode.Equal(),
                )
            }
        }.toSet()

        return CustomFilterEditorOptions(
            label = label,
            areas = targetAreas,
            pathCriteria = pathCriteria,
            saveAsEnabled = true,
        ).also { log(TAG, INFO) { "Editor options are : $it" } }
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "CustomFilter", "EditorOptions", "Creator")
    }
}