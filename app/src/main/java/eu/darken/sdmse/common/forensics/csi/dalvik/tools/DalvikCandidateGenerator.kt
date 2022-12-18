package eu.darken.sdmse.common.forensics.csi.dalvik.tools

import dagger.Reusable
import eu.darken.sdmse.common.Architecture
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.csi.dalvik.DalvikCheck
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject

/**
 * Hints
 * https://android.googlesource.com/platform/frameworks/base/+/a029ea1/services/java/com/android/server/pm/PackageManagerService.java#1256
 * https://android.googlesource.com/platform/libcore-snapshot/+/ics-mr1/dalvik/src/main/java/dalvik/system/DexPathList.java
 * https://android.googlesource.com/platform/dalvik/+/39e8b7e/dexopt/OptMain.cpp
 */
@Reusable
class DalvikCandidateGenerator @Inject constructor(
    private val areaManager: DataAreaManager,
    private val architecture: Architecture,
) : DalvikCheck {

    @Volatile
    private var _sourceAreas: Set<LocalPath>? = null
    private val lock = Mutex()
    private suspend fun getSourcePaths(): Collection<LocalPath> = lock.withLock {
        _sourceAreas?.let { return@withLock it }

        val newSourcePaths = HashSet<LocalPath>()
        val currentAreas = areaManager.currentAreas()

        for (area in currentAreas.filter { it.type == DataArea.Type.APP_APP }) {
            newSourcePaths.add(area.path as LocalPath)
        }
        for (area in currentAreas.filter { it.type == DataArea.Type.SYSTEM_APP }) {
            newSourcePaths.add(area.path as LocalPath)
        }
        for (area in currentAreas.filter { it.type == DataArea.Type.SYSTEM }) {
            newSourcePaths.add(LocalPath.build(area.path as LocalPath, "framework"))
        }

        return newSourcePaths.also {
            _sourceAreas = it
        }
    }

    private fun removePostFix(fileName: String, removeExtension: Boolean): String {
        var postFixCutoff = -1
        for (ext in POSTFIX_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                postFixCutoff = fileName.lastIndexOf(ext)
                break
            }
        }
        if (postFixCutoff == -1) {
            for (post in DEX_EXTENSIONS) {
                val extCutIndex = fileName.lastIndexOf(post)
                if (extCutIndex == -1) continue
                val removedExt = fileName.substring(0, extCutIndex)
                for (ext in SOURCE_EXTENSIONS) {
                    if (removedExt.endsWith(ext)) {
                        postFixCutoff = extCutIndex
                        break
                    }
                }
                if (postFixCutoff != -1) break
            }
        }
        var result = fileName
        if (postFixCutoff != -1) {
            // something.jar@classes.dex -> // something.jar
            result = fileName.substring(0, postFixCutoff)
            if (removeExtension) {
                // something.jar -> something
                val extraExtension = result.lastIndexOf(".")
                if (extraExtension != -1) result = result.substring(0, extraExtension)
            }
        }
        return result
    }

    private fun fileNameToPath(fileName: String): LocalPath {
        var pathFromName = fileName.replace("@", File.separator)
        if (!pathFromName.startsWith("/")) pathFromName = File.separator + pathFromName
        return LocalPath.build(pathFromName)
    }

    suspend fun getCandidates(dexFile: LocalPath): Collection<LocalPath> {
        val start = System.currentTimeMillis()
        val candidates = LinkedHashSet<LocalPath>()

        // Dex file contains the direct path
        // system@framework@boot.oat -> /system/framework/boot.oat
        val nameAsPath: LocalPath = fileNameToPath(dexFile.name)
        candidates.add(nameAsPath)

        // data@app@com.test.apk@classes.dex -> /data/app/com.test.apk
        val pathWithoutPostFix: LocalPath = fileNameToPath(removePostFix(dexFile.name, false))
        candidates.add(pathWithoutPostFix)

        // data@app@com.test.apk@classes.dex -> /data/app/com.test
        val pathWithoutExtension: LocalPath = fileNameToPath(removePostFix(dexFile.name, true))
        for (ext in SOURCE_EXTENSIONS) {
            candidates.add(LocalPath.build(pathWithoutExtension.parent()!!, pathWithoutExtension.name + ext))
        }

        // Account for architecture in direct and indirect matches
        // /data/dalvik-cache/x86/system@framework@boot.oat -> /system/framework/x86/boot.oat
        for (folder in architecture.folderNames) {
            val argFolder = File.separator + folder + File.separator
            if (dexFile.path.contains(argFolder)) {
                candidates.add(
                    LocalPath.build(pathWithoutPostFix.parent()!!, argFolder, pathWithoutPostFix.name)
                )
                // Do this for all storages
                for (parent in getSourcePaths()) {
                    candidates.add(
                        LocalPath.build(parent, argFolder, pathWithoutPostFix.name)
                    )
                }
            }
        }

        // Source has different extension
        for (parent in getSourcePaths()) {
            // Target has a direct name match on a different location
            candidates.add(LocalPath.build(parent, dexFile.name))
            // We have something like test.apk@classes.dex and a possible direct match, just different storage
            candidates.add(LocalPath.build(parent, pathWithoutPostFix.name))
            // Webview.apk@classes.dex -> Webview/base.apk
            candidates.add(LocalPath.build(parent, pathWithoutExtension.name + File.separator + "base.apk"))
            // Webview.dex -> Webview/Webview.apk
            candidates.add(
                LocalPath.build(
                    parent,
                    pathWithoutExtension.name + File.separator + pathWithoutPostFix.name
                )
            )
            for (extension in SOURCE_EXTENSIONS) {
                candidates.add(LocalPath.build(parent, pathWithoutExtension.name + extension))
            }
        }
        val stop = System.currentTimeMillis()
        if (Bugs.isTrace) {
            log(TAG) { "Generation time: ${stop - start}" }
            for (p in candidates) log(TAG, VERBOSE) { "Potential parent: $p" }
        }
        return candidates
    }

    companion object {
        val TAG: String = logTag("CSI", "Dalvik", "Dex", "CandidateGenerator")

        private val POSTFIX_EXTENSIONS = arrayOf(
            "@classes.dex",
            "@classes.odex",
            "@classes.dex.art",
            "@classes.oat",
            "@classes.vdex"
        )
        private val DEX_EXTENSIONS = arrayOf(
            ".dex",
            ".odex",
            ".oat",
            ".art",
            ".vdex"
        )
        private val SOURCE_EXTENSIONS = arrayOf(
            ".apk",
            ".jar",
            ".zip"
        )
    }
}