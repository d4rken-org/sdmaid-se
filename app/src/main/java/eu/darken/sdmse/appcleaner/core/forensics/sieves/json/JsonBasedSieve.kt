package eu.darken.sdmse.appcleaner.core.forensics.sieves.json

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.isCaseInsensitive
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.containsSegments
import eu.darken.sdmse.common.files.joinSegments
import eu.darken.sdmse.common.files.startsWith
import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.common.openAsset
import eu.darken.sdmse.common.pkgs.Pkg
import okio.BufferedSource
import okio.buffer


class JsonBasedSieve @AssistedInject constructor(
    @Assisted private val assetPath: String,
    @ApplicationContext private val context: Context,
    private val baseMoshi: Moshi
) {

    private val filterDb: SieveJsonDb = run {
        val moshi = baseMoshi.newBuilder().apply {
//            add(RegexAdapter(caseInsensitive = true))
        }.build()
        val source: BufferedSource = context.openAsset(assetPath).buffer()
        moshi.adapter<SieveJsonDb>().fromJson(source)!!
    }

    private val regexCache = mutableMapOf<String, Regex>()
    private val regexCacheIgnoreCase = mutableMapOf<String, Regex>()

    fun matches(
        pkgId: Pkg.Id,
        areaType: DataArea.Type,
        target: Segments,
    ): Boolean = filterDb.appFilters
        .mapNotNull { appFilter ->
            val pkgNames = appFilter.packageNames
            if (pkgNames.isNullOrEmpty() || appFilter.packageNames.any { it == pkgId.name }) {
                appFilter.fileFilters
            } else {
                null
            }
        }
        .flatten()
        .filter { it.areaTypes.isEmpty() || it.areaTypes.contains(areaType) }
        .any match@{ fileFilter ->

            val ignoreCase = areaType.isCaseInsensitive

            fileFilter.notContains?.takeIf { it.isNotEmpty() }?.let { excls ->
                val excluded = excls.any {
                    target.containsSegments(it.toSegs(), ignoreCase = ignoreCase, allowPartial = true)
                }
                if (excluded) return@match false
            }

            val startsWithCondition = fileFilter.startsWith
                ?.takeIf { it.isNotEmpty() }
                ?.let { starters ->
                    starters.any { target.startsWith(it.toSegs(), ignoreCase = ignoreCase, allowPartial = true) }
                }
                ?: true

            val containsCondition = fileFilter.contains
                ?.takeIf { it.isNotEmpty() }
                ?.let { contains ->
                    contains.any { target.containsSegments(it.toSegs(), ignoreCase = ignoreCase, allowPartial = true) }
                }
                ?: true

            val regexCondition = fileFilter.patterns
                ?.takeIf { it.isNotEmpty() }
                ?.map { rawPattern ->
                    if (ignoreCase) {
                        regexCacheIgnoreCase[rawPattern] ?: Regex(rawPattern, RegexOption.IGNORE_CASE).also {
                            regexCacheIgnoreCase[rawPattern] = it
                        }
                    } else {
                        regexCache[rawPattern] ?: Regex(rawPattern).also {
                            regexCache[rawPattern] = it
                        }
                    }
                }
                ?.let { regexes ->
                    val segsAsPath = target.joinSegments()
                    regexes.any { it.matches(segsAsPath) }
                }
                ?: true

            return@match startsWithCondition && containsCondition && regexCondition
        }

    @AssistedFactory
    interface Factory {
        fun create(assetPath: String): JsonBasedSieve
    }
}