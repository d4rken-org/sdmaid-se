package eu.darken.sdmse.common

import eu.darken.sdmse.common.debug.logging.logTag
import java.io.File

private val TAG: String = logTag("FileOpsHelper")

//    fun formatOctalPermissionToReadable(octalPermission: Int): String {
//        val _octal = String.format(Locale.US, "%03d", octalPermission)
//        val sb = StringBuilder()
//        for (i in 0 until _octal.length) {
//            val num = _octal[i].toString().toInt()
//            sb.append(if (num and 4 == 0) '-' else 'r')
//            sb.append(if (num and 2 == 0) '-' else 'w')
//            sb.append(if (num and 1 == 0) '-' else 'x')
//        }
//        return sb.toString()
//    }
//
//    @Throws(FileNotFoundException::class) fun deleteRecursive(path: File): Boolean {
//        if (!path.exists()) throw FileNotFoundException(path.absolutePath)
//        var ret = true
//        if (path.isDirectory) {
//            val files = path.listFiles()
//            if (files != null) {
//                for (f in files) ret = ret && deleteRecursive(f)
//            }
//        }
//        Timber.tag(TAG).v("deleteRecursive: %s", path.path)
//        return ret && path.delete()
//    }
//
//    fun getAllParents(child: File): List<String> {
//        val parents: MutableList<String> = ArrayList()
//        var _parent = child.parentFile
//        while (_parent != null) {
//            parents.add(_parent.absolutePath)
//            _parent = _parent.parentFile
//        }
//        return parents
//    }
//
//    fun findTreeRoots(files: Collection<SDMFile?>): Map<SDMFile, Collection<SDMFile>> {
//        // HashSet for better contains performance.
//        val woodwork: Collection<SDMFile> = HashSet<Any?>(files)
//        val roots: MutableCollection<SDMFile> = HashSet<Any?>(files)
//        val branches: MutableCollection<SDMFile> = HashSet<SDMFile>()
//        for (file in files) {
//            // Is this files parent part of our woodwork?
//            if (woodwork.contains(file.getParentFile())) {
//                // Parent is part of the wood work so this is not a root.
//                roots.remove(file)
//                // Because this is not a root, it's a branch.
//                branches.add(file)
//            }
//        }
//        val result: MutableMap<SDMFile, Collection<SDMFile>> = HashMap<SDMFile, Collection<SDMFile>>()
//        for (root in roots) {
//            val children: MutableCollection<SDMFile> = HashSet<SDMFile>()
//            val it: MutableIterator<SDMFile> = branches.iterator()
//            while (it.hasNext()) {
//                val potChild: SDMFile = it.next()
//                if (potChild.getPath().startsWith(root.getPath() + File.separator)) {
//                    children.add(potChild)
//                    it.remove()
//                }
//            }
//            result[root] = children
//        }
//        return result
//    }
//
//    fun excludeNestedStructure(files: MutableCollection<SDMFile>, exclusions: List<Exclusion?>) {
//        if (files.isEmpty()) return
//        val indirectlyExcludedParents = HashSet<String>()
//        for (ex in exclusions) {
//            val it: MutableIterator<SDMFile> = files.iterator()
//            while (it.hasNext()) {
//                val item: SDMFile = it.next()
//                if (ex.match(item.getPath())) {
//                    indirectlyExcludedParents.addAll(getAllParents(item.getJavaFile()))
//                    it.remove()
//                }
//            }
//        }
//        if (indirectlyExcludedParents.isEmpty()) return
//        val it: MutableIterator<SDMFile> = files.iterator()
//        while (it.hasNext()) {
//            val item: SDMFile = it.next()
//            if (indirectlyExcludedParents.contains(item.getPath())) {
//                Timber.tag(TAG).v("Indirectly excluded parent: %s", item)
//                it.remove()
//            }
//        }
//    }
//
//    fun findMount(mountCollection: Collection<Mount?>, target: SDMFile): Mount? {
//        return findMount(mountCollection, target.getJavaFile())
//    }
//
//    fun findMount(mountCollection: Collection<Mount?>, target: File?): Mount? {
//        if (target == null) return null
//        var currentTarget: File? = File(target.path)
//        try {
//            while (currentTarget != null) {
//                for (mount in mountCollection) {
//                    if (mount.getMountpoint().getPath().equals(currentTarget.path)) {
//                        return mount
//                    }
//                }
//                currentTarget = currentTarget.parentFile
//            }
//        } catch (e: Exception) {
//            Timber.tag(TAG).w(e, "Error while trying to find mountpoint.")
//        }
//        Timber.tag(TAG).w("Couldn't find mountpoint for: %s", target)
//        return null
//    }
//
//    fun getUncoveredPaths(touncover: Collection<SDMFile?>): Collection<SDMFile> {
//        val uncovered: MutableCollection<SDMFile> = HashSet<Any?>(touncover)
//        // which are covered, because they are mounted inside another one?
//        for (parent in touncover) {
//            for (child in touncover) {
//                if (!parent.equals(child)) {
//                    if (isChildOf(parent, child)) uncovered.remove(child)
//                }
//            }
//        }
//        return uncovered
//    }

fun String.pathChopOffLast(): String? {
    val cutOff = lastIndexOf(File.separator)
    return if (cutOff == -1) null else substring(0, cutOff)
}

fun String.getFirstDirElement(seperator: String = File.separator): String {
    var workPath = this
    if (workPath.startsWith(File.separator)) {
        workPath = workPath.substring(1)
    }
    val names = workPath.split(seperator).toTypedArray()
    return names[0]
}

//    @Throws(IOException::class) fun copy(src: SDMFile, dst: SDMFile) {
//        Timber.tag(TAG).d("Copying %s -> %s", src, dst)
//        var `in`: InputStream? = null
//        var out: OutputStream? = null
//        if (dst.getJavaFile().exists() && !dst.getJavaFile().canWrite()) {
//            val writable: Boolean = dst.getJavaFile().setWritable(true)
//            Timber.tag(TAG).d("Target exists, but isn't writable, correcting... success: %b", writable)
//        }
//        try {
//            `in` = FileInputStream(src.getPath())
//            out = FileOutputStream(dst.getPath())
//            val buffer = ByteArray(4096)
//            var length: Int
//            while (`in`!!.read(buffer).also { length = it } > 0) out!!.write(buffer, 0, length)
//        } finally {
//            try {
//                `in`?.close()
//            } catch (ignore: Exception) {
//            }
//            try {
//                out?.close()
//            } catch (ignore: Exception) {
//            }
//        }
//    }
//
//    fun fileToString(files: Collection<SDMFile?>): Collection<String> {
//        val strings: MutableCollection<String> = ArrayList()
//        for (file in files) strings.add(file.getPath())
//        return strings
//    }
