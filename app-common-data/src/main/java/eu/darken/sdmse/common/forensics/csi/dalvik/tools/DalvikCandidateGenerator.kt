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
import eu.darken.sdmse.common.files.local.LocalPath
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

        currentAreas
            .filter { it.type == DataArea.Type.APP_APP }
            .map { it.path }
            .filterIsInstance<LocalPath>()
            .forEach { newSourcePaths.add(it) }

        currentAreas
            .filter { it.type == DataArea.Type.SYSTEM_APP }
            .map { it.path }
            .filterIsInstance<LocalPath>()
            .forEach { newSourcePaths.add(it) }

        currentAreas
            .filter { it.type == DataArea.Type.APEX }
            .map { it.path }
            .filterIsInstance<LocalPath>()
            .forEach { newSourcePaths.add(it) }

        currentAreas
            .filter { it.type == DataArea.Type.SYSTEM }
            .map { it.path }
            .filterIsInstance<LocalPath>()
            .forEach { newSourcePaths.add(LocalPath.build(it, "framework")) }

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

    private fun fileNameToPathVariants(fileName: String): Collection<LocalPath> {
        val variants = mutableSetOf<LocalPath>()

        fileName.replace("@", File.separator)
            .let { if (!it.startsWith("/")) File.separator + it else it }
            .let { variants.add(LocalPath.build(it)) }

        /**
         * apex@com.android.permission@priv-app@GooglePermissionController@M_2022_06@GooglePermissionController.apk
         * to
         * /apex/com.android.permission/priv-app/GooglePermissionController@M_2022_06/GooglePermissionController.apk
         */
        MODDATE_PART1.findAll(fileName).lastOrNull()
            ?.let { it.groupValues[1] }
            ?.let {
                try {
                    Regex("@$it@(\\w+)@$it")
                } catch (e: Exception) {
                    null
                }
            }
            ?.find(fileName)
            ?.let { it.groupValues[1] }
            ?.let { fileName.replace("@", File.separator).replace(File.separatorChar + it, "@$it") }
            ?.let { if (!it.startsWith("/")) File.separator + it else it }
            ?.let { variants.add(LocalPath.build(it)) }

        return variants
    }

    suspend fun getCandidates(dexFile: LocalPath): Collection<LocalPath> {
        val start = System.currentTimeMillis()
        val candidates = LinkedHashSet<LocalPath>()

        // Dex file contains the direct path
        // system@framework@boot.oat -> /system/framework/boot.oat
        candidates.addAll(fileNameToPathVariants(dexFile.name))

        // data@app@com.test.apk@classes.dex -> /data/app/com.test.apk
        val pathsWithoutPostFix = fileNameToPathVariants(removePostFix(dexFile.name, false))
        candidates.addAll(pathsWithoutPostFix)

        // Account for architecture in direct and indirect matches
        // /data/dalvik-cache/x86/system@framework@boot.oat -> /system/framework/x86/boot.oat
        pathsWithoutPostFix.forEach { pathWithoutPostFix ->
            architecture.folderNames.forEach { folder ->
                val argFolder = File.separator + folder + File.separator
                if (dexFile.path.contains(argFolder)) {
                    candidates.add(
                        LocalPath.build(pathWithoutPostFix.parent()!!, argFolder, pathWithoutPostFix.name)
                    )
                    // Do this for all storages
                    getSourcePaths().forEach { parent ->
                        candidates.add(
                            LocalPath.build(parent, argFolder, pathWithoutPostFix.name)
                        )
                    }
                }
            }
        }

        // data@app@com.test.apk@classes.dex -> /data/app/com.test
        val pathsWithoutExtension = fileNameToPathVariants(removePostFix(dexFile.name, true))
        pathsWithoutExtension.forEach {
            for (ext in SOURCE_EXTENSIONS) {
                candidates.add(LocalPath.build(it.parent()!!, it.name + ext))
            }
        }
        pathsWithoutPostFix.forEach { pathWithoutPostFix ->
            pathsWithoutExtension.forEach { pathWithoutExtension ->
                // Source has different extension
                getSourcePaths().forEach { parent ->
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
                    SOURCE_EXTENSIONS.forEach { extension ->
                        candidates.add(LocalPath.build(parent, pathWithoutExtension.name + extension))
                    }
                }
            }
        }

        val cleaned = candidates.filter { it.path[0] == File.separatorChar }

        val stop = System.currentTimeMillis()
        if (Bugs.isTrace) {
            log(TAG) { "Generation time: ${stop - start}" }
            for (p in cleaned) log(TAG, VERBOSE) { "Potential parent: $p" }
        }
        return cleaned
    }

    companion object {
        val TAG: String = logTag("CSI", "Dalvik", "Dex", "CandidateGenerator")

        /**
         * apex@com.android.permission@priv-app@GooglePermissionController@M_2022_06@GooglePermissionController.apk@classes.vdex
         * to
         * GooglePermissionController
         */
        private val MODDATE_PART1 = Regex("@(\\w+)(?:\\.\\w+)?$")
        private val POSTFIX_EXTENSIONS = arrayOf(
            "@classes.dex",
            "@classes.odex",
            "@classes.dex.art",
            "@classes.oat",
            "@classes.vdex",
            "@classes.art",
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