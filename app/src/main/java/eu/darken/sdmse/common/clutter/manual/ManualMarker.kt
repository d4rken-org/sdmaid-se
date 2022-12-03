package eu.darken.sdmse.common.clutter.manual

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.restrictedCharset
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.hashCode
import java.util.regex.Pattern

data class ManualMarker(
    private val pkgs: Set<String>,
    override val areaType: DataArea.Type,
    private val path: String?,
    private val contains: String?,
    private val regex: String?,
    override val flags: Set<Marker.Flag> = emptySet()
) : Marker {

    private val ignoreCase: Boolean
        get() = areaType.restrictedCharset

    private val pattern by lazy {
        regex?.let {
            Pattern.compile(it, if (ignoreCase) Pattern.CASE_INSENSITIVE else 0)
        }
    }

    init {
        if (Bugs.isDebug && this.regex != null) pattern
    }

    override val prefixFreeBasePath: String
        get() = path ?: ""

    override val isPrefixFreeBasePathDirect: Boolean
        get() = regex == null

    override fun match(areaType: DataArea.Type, prefixFree: String): Marker.Match? {
        if (this.areaType !== areaType) return null
        if (prefixFree.isEmpty()) return null
        require(prefixFree[0] != '/') { "Not prefixFree: $prefixFree" }

        val match = when {
            path != null && regex == null -> {
                prefixFree.equals(path, ignoreCase)
            }
            regex != null -> {
                if (path != null && !prefixFree.startsWith(path, ignoreCase)) {
                    false
                } else if (contains != null && !prefixFree.contains(contains, ignoreCase)) {
                    false
                } else {
                    pattern!!.matcher(prefixFree).matches()
                }
            }
            else -> false
        }

        return if (match) Marker.Match(pkgs, flags) else null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ManualMarker
        if (pkgs != that.pkgs) return false
        if (!path.equals(that.path, ignoreCase)) return false
        if (!contains.equals(that.contains, ignoreCase)) return false
        if (!regex.equals(that.regex, ignoreCase)) return false
        return if (flags != that.flags) false else areaType === that.areaType
    }

    override fun hashCode(): Int {
        var result = pkgs.hashCode()
        result = 31 * result + path.hashCode(ignoreCase)
        result = 31 * result + contains.hashCode(ignoreCase)
        result = 31 * result + regex.hashCode(ignoreCase)
        result = 31 * result + flags.hashCode()
        result = 31 * result + areaType.hashCode()
        return result
    }

    override fun toString(): String = String.format(
        "ManualMarker(location=%s, path=%s, regex=%s, flags=%s, pkgs=%s)", areaType, path, regex, flags, pkgs
    )
}