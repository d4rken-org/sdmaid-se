package eu.darken.sdmse.common.files.saf

import android.net.Uri
import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.TypeMissMatchException
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.joinSegments
import kotlinx.parcelize.Parcelize
import java.io.File

@Keep @Parcelize
@JsonClass(generateAdapter = true)
data class SAFPath(
    internal val treeRoot: String,
    override val segments: List<String>,
) : APath {

    val treeRootUri: Uri
        get() = Uri.parse(treeRoot)

    init {
        val paths = treeRootUri.pathSegments
        require(paths.size >= 2 && "tree" == paths[0]) { "SAFFile URI's must be a tree uri: $treeRoot" }
    }

    override val userReadableName: CaString
        get() = super.userReadableName

    override val userReadablePath: CaString
        get() {
            val treeRootPath = treeRootUri.path
            return when {
                treeRootPath?.startsWith("/tree/primary") == true -> caString {
                    "/storage/emulated/0/${segments.joinSegments("/")}"
                }

                treeRootPath?.let { URIPATH_ID_REGEX.matches(it) } == true -> caString {
                    val storageId = URIPATH_ID_REGEX.matchEntire(treeRootPath)
                    "/storage/${storageId?.groupValues?.get(1)}/${segments.joinSegments("/")}"
                }

                else -> super.userReadablePath
            }
        }

    override var pathType: APath.PathType
        get() = APath.PathType.SAF
        set(value) {
            TypeMissMatchException.check(value, pathType)
        }

    override val path: String
        get() = "${File.separator}${(treeRootUri.pathSegments + segments).joinToString(File.separator)}"

    val pathUri: Uri
        get() {
            if (segments.isEmpty()) return treeRootUri

            val uriString = StringBuilder(treeRoot).apply {
                append("%3A") // Uri.encode(":")
                segments.forEach {
                    append(Uri.encode(it))
                    if (it != segments.last()) {
                        append("%2F") // Uri.encode(File.separator)
                    }
                }
            }
            return Uri.parse(uriString.toString())
        }

    override val name: String
        get() = when {
            segments.isNotEmpty() -> segments.last()
            else -> treeRootUri.pathSegments.last().split('/').last()
        }

    override fun child(vararg segments: String): SAFPath {
        return build(this.treeRoot, *this.segments.toTypedArray(), *segments)
    }

    override fun toString(): String = "SAFPath(treeRoot=$treeRoot, segments=$segments)"

    companion object {
        fun build(base: String, vararg segs: String): SAFPath = SAFPath(base, segs.toList())

        fun build(base: Uri, vararg segs: String): SAFPath = build(base.toString(), *segs)

        private val URIPATH_ID_REGEX by lazy { Regex("^/tree/([a-z0-9-]+)(?::.+?)*\$") }
    }
}