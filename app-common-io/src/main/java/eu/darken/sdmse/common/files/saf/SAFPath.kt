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

@Suppress("BlockingMethodInNonBlockingContext")
@Keep @Parcelize
@JsonClass(generateAdapter = true)
data class SAFPath(
    val treeRoot: Uri,
    override val segments: List<String>,
) : APath {

    init {
        require(SAFGateway.isTreeUri(treeRoot)) { "SAFFile URI's must be a tree uri: $treeRoot" }
    }

    override val userReadableName: CaString
        get() = super.userReadableName

    override val userReadablePath: CaString
        get() = when {
            treeRoot.path?.startsWith("/tree/primary") == true -> caString {
                "/storage/emulated/0/${segments.joinSegments("/")}"
            }
            treeRoot.path?.let { URIPATH_ID_REGEX.matches(it) } == true -> caString {
                val storageId = URIPATH_ID_REGEX.matchEntire(treeRoot.path!!)
                "/storage/${storageId?.groupValues?.get(1)}/${segments.joinSegments("/")}"
            }
            else -> super.userReadablePath
        }

    override var pathType: APath.PathType
        get() = APath.PathType.SAF
        set(value) {
            TypeMissMatchException.check(value, pathType)
        }

    override val path: String
        get() = "${File.separator}${(treeRoot.pathSegments + segments).joinToString(File.separator)}"

    val pathUri by lazy {
        if (segments.isEmpty()) return@lazy treeRoot

        val uriString = StringBuilder(treeRoot.toString()).apply {
            append(Uri.encode(":"))
            segments.forEach {
                append(Uri.encode(it))
                if (it != segments.last()) append(Uri.encode(File.separator))
            }
        }
        Uri.parse(uriString.toString())
    }

    override val name: String
        get() = if (segments.isNotEmpty()) {
            segments.last()
        } else {
            treeRoot.pathSegments.last().split('/').last()
        }

    override fun child(vararg segments: String): SAFPath {
        return build(this.treeRoot, *this.segments.toTypedArray(), *segments)
    }

    override fun toString(): String = "SAFFile(treeRoot=$treeRoot, segments=$segments)"

    companion object {
        fun build(base: Uri, vararg segs: String): SAFPath {
            return SAFPath(base, segs.toList())
        }

        private val URIPATH_ID_REGEX by lazy { Regex("^/tree/([a-z0-9-]+)(?::.+?)*\$") }
    }
}