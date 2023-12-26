package eu.darken.sdmse.appcleaner.core.forensics

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.progress.Progress
import kotlin.reflect.KClass

interface ExpendablesFilter : Progress.Host, Progress.Client {

    val identifier: ExpendablesFilterIdentifier
        get() = this.javaClass.kotlin

    suspend fun initialize()

    suspend fun match(
        pkgId: Pkg.Id,
        target: APathLookup<APath>,
        areaType: DataArea.Type,
        segments: Segments
    ): Match?

    suspend fun process(matches: Collection<Match>)

    interface Match {
        val identifier: ExpendablesFilterIdentifier
        val lookup: APathLookup<*>
        val path: APath
            get() = lookup.lookedUp

        val expectedGain: Long

        data class Deletion(
            override val identifier: ExpendablesFilterIdentifier,
            override val lookup: APathLookup<*>,
        ) : Match {
            override val expectedGain: Long
                get() = lookup.size
        }
    }

    interface Factory {
        suspend fun isEnabled(): Boolean
        suspend fun create(): ExpendablesFilter
    }
}

typealias ExpendablesFilterIdentifier = KClass<out ExpendablesFilter>