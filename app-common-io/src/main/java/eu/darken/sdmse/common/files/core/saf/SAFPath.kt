package eu.darken.sdmse.common.files.core.saf

import android.net.Uri
import androidx.annotation.Keep
import androidx.documentfile.provider.DocumentFile
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.TypeMissMatchException
import eu.darken.sdmse.common.castring.CaString
import eu.darken.sdmse.common.files.core.APath
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.io.File

@Suppress("BlockingMethodInNonBlockingContext")
@Keep @Parcelize
@JsonClass(generateAdapter = true)
data class SAFPath(
    internal val treeRoot: Uri,
    internal val crumbs: List<String>
) : APath {

    init {
        require(SAFGateway.isTreeUri(treeRoot)) { "SAFFile URI's must be a tree uri: $treeRoot" }
    }

    override val userReadableName: CaString
        get() = super.userReadableName

    override val userReadablePath: CaString
        get() = super.userReadablePath

    override var pathType: APath.PathType
        get() = APath.PathType.SAF
        set(value) {
            TypeMissMatchException.check(value, pathType)
        }

    override val path: String
        get() {
            return if (crumbs.isNotEmpty()) {
                crumbs.joinToString(File.pathSeparator)
            } else {
                treeRoot.pathSegments.joinToString(File.pathSeparator)
            }
        }

    val pathUri by lazy {
        val uriString = StringBuilder(treeRoot.toString()).apply {
            append(Uri.encode(":"))
            crumbs.forEach {
                append(Uri.encode(it))
                if (it != crumbs.last()) append(Uri.encode(File.separator))
            }
        }
        Uri.parse(uriString.toString())
    }

    override val name: String
        get() = if (crumbs.isNotEmpty()) {
            crumbs.last()
        } else {
            treeRoot.pathSegments.last().split('/').last()
        }

    @IgnoredOnParcel
    override val segments: List<String>
        get() = crumbs

    override fun child(vararg segments: String): SAFPath {
        return build(this.treeRoot, *this.crumbs.toTypedArray(), *segments)
    }

    override fun toString(): String = "SAFFile(treeRoot=$treeRoot, crumbs=$crumbs)"

    companion object {
        fun build(documentFile: DocumentFile): SAFPath {
            return build(documentFile.uri)
        }

        fun build(base: Uri, vararg segs: String): SAFPath {
            return SAFPath(base, segs.toList())
        }
    }
}